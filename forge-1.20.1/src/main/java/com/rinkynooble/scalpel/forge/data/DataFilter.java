package com.rinkynooble.scalpel.forge.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.rinkynooble.scalpel.core.ContentType;
import com.rinkynooble.scalpel.core.Decision;
import com.rinkynooble.scalpel.core.RecipeMode;
import com.rinkynooble.scalpel.core.Resolver;
import com.rinkynooble.scalpel.core.ScalpelCore;
import com.rinkynooble.scalpel.core.json.JsonScrub;
import com.rinkynooble.scalpel.core.json.RecipeIngredients;
import com.rinkynooble.scalpel.core.json.RecipeRewriter;
import com.rinkynooble.scalpel.forge.Scalpel;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Filters data files right after they are read and before anything parses them: recipes, advancements,
 * loot tables and loot modifiers. Anything dropped here is never parsed, so a removed id never produces
 * an "unknown item" error.
 */
public final class DataFilter {
    private static final JsonScrub.Policy LOOT = new JsonScrub.Policy(JsonScrub.EmptyArrays.KEEP, Set.of("conditions", "functions"));
    private static final String LOOT_MODIFIER_INDEX = "forge:global_loot_modifiers";

    /** Items that were the result of a dropped recipe, for the cascade report. */
    private static final Set<String> lostProducers = ConcurrentHashMap.newKeySet();
    /** Recipe ids that came from files, so the final pass knows which recipes scripts added. */
    private static final Set<String> scannedRecipes = ConcurrentHashMap.newKeySet();
    /**
     * Every output a recipe file names, for recipes with more than one (Farmer's Delight's cutting board makes a log
     * and tree bark). A built recipe only shows its first; the cascade report needs them all.
     */
    private static final Map<String, Set<String>> moreOutputs = new ConcurrentHashMap<>();
    /** Loot modifier files dropped by this load, which Forge's index must skip. */
    private static final Set<ResourceLocation> droppedLootModifiers = ConcurrentHashMap.newKeySet();

    private DataFilter() {
    }

    public static void beginDataLoad() {
        lostProducers.clear();
        scannedRecipes.clear();
        moreOutputs.clear();
        droppedLootModifiers.clear();
        TagFilter.reset();
        CutLoot.reset();
        Scalpel.core().beginDataLoad();
    }

    public static Set<String> lostProducers() {
        return lostProducers;
    }

    public static boolean wasScanned(String recipeId) {
        return scannedRecipes.contains(recipeId);
    }

    /** The outputs a recipe's file names, when it names more than one; empty otherwise. */
    public static Set<String> outputsNamed(String recipeId) {
        return moreOutputs.getOrDefault(recipeId, Set.of());
    }

    public static boolean isDroppedLootModifier(ResourceLocation id) {
        return droppedLootModifiers.contains(id);
    }

    /** Called with every directory scanned through {@code SimpleJsonResourceReloadListener#scanDirectory}. */
    public static void afterScan(String directory, Map<ResourceLocation, JsonElement> files) {
        ScalpelCore core = Scalpel.core();
        switch (directory) {
            case "recipes" -> recipes(core, files);
            case "advancements" -> advancements(core, files);
            case "loot_tables" -> lootTables(core, files);
            case "loot_modifiers" -> lootModifiers(core, files);
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ recipes

    private static void recipes(ScalpelCore core, Map<ResourceLocation, JsonElement> files) {
        for (ResourceLocation id : files.keySet()) {
            scannedRecipes.add(id.toString());
        }
        Resolver resolver = core.resolver();
        boolean anyCut = !resolver.cutIds(ContentType.ITEM).isEmpty();
        if (!anyCut && !resolver.rules().hasRulesFor(ContentType.RECIPE)) {
            core.report().evaluated(Set.of(ContentType.RECIPE));
            return;
        }
        if (core.settings().cascadeDepth() > 0) {
            for (Map.Entry<ResourceLocation, JsonElement> entry : files.entrySet()) {
                Set<String> outputs = outputIds(entry.getValue());
                if (outputs.size() > 1) {
                    moreOutputs.put(entry.getKey().toString(), outputs);
                }
            }
        }
        Predicate<String> isCut = id -> resolver.isCut(ContentType.ITEM, id);
        Iterator<Map.Entry<ResourceLocation, JsonElement>> it = files.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<ResourceLocation, JsonElement> entry = it.next();
            String id = entry.getKey().toString();
            Decision rule = resolver.decide(ContentType.RECIPE, id);
            if (rule.action() == Decision.Action.REMOVE) {
                change(core, "recipe removed by rule", id, rule.rule().location());
                lostOutputs(entry.getValue());
                if (core.applies()) {
                    it.remove();
                }
                continue;
            }
            if (!anyCut || !entry.getValue().isJsonObject()) {
                continue;
            }
            Set<String> refs = JsonScrub.findReferences(entry.getValue(), isCut);
            if (refs.isEmpty()) {
                continue;
            }
            if (rule.action() == Decision.Action.KEEP) {
                core.warn("recipe " + id + " is kept by " + rule.rule().location() + " but uses cut content " + refs);
                continue;
            }
            if (modeFor(core, refs) == RecipeMode.REWRITE) {
                JsonObject copy = entry.getValue().getAsJsonObject().deepCopy();
                RecipeRewriter.Outcome outcome = RecipeRewriter.rewrite(copy, isCut);
                if (outcome == RecipeRewriter.Outcome.REWRITTEN) {
                    change(core, "recipe rewritten", id, "without " + String.join(", ", refs));
                    if (core.applies()) {
                        entry.setValue(copy);
                    }
                    continue;
                }
            }
            change(core, "recipe dropped", id, "uses " + String.join(", ", refs));
            lostOutputs(entry.getValue());
            if (core.applies()) {
                it.remove();
            }
        }
        core.report().evaluated(Set.of(ContentType.RECIPE));
    }

    /**
     * Recipe types whose built recipes show their vanilla ingredients, so the final recipe pass reads them after every
     * script ran (and can rewrite crafting recipes). Every other type is checked here, from its file.
     */
    private static final Set<String> READ_BY_FINAL_PASS = Set.of("minecraft:crafting_shaped", "minecraft:crafting_shapeless",
            "minecraft:smelting", "minecraft:blasting", "minecraft:smoking", "minecraft:campfire_cooking",
            "minecraft:stonecutting");

    /**
     * Called at the start of {@code RecipeManager#apply}, when tags are known (every reload listener has prepared by
     * then) and before scripts change recipes. A recipe of a type Scalpel has no model for, such as TaCZ's gun smith
     * table or Mekanism's machines, keeps an ingredient whose tag the cuts emptied; the mod then shows or matches an
     * ingredient with no items, and TaCZ's screen divided by it. Those recipes are dropped, whatever the recipe mode,
     * since only crafting recipes can be rewritten.
     */
    public static void beforeRecipeApply(Map<ResourceLocation, JsonElement> recipes) {
        Set<String> emptied = TagFilter.emptiedItemTags();
        if (emptied.isEmpty()) {
            return;
        }
        ScalpelCore core = Scalpel.core();
        Resolver resolver = core.resolver();
        Predicate<String> isCut = id -> resolver.isCut(ContentType.ITEM, id);
        Iterator<Map.Entry<ResourceLocation, JsonElement>> it = recipes.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<ResourceLocation, JsonElement> entry = it.next();
            if (!entry.getValue().isJsonObject()) {
                continue;
            }
            JsonObject json = entry.getValue().getAsJsonObject();
            String type = json.has("type") && json.get("type").isJsonPrimitive() ? JsonScrub.asId(json.get("type").getAsString()) : null;
            if (type == null || READ_BY_FINAL_PASS.contains(type) || type.equals("forge:conditional")) {
                continue;
            }
            Set<String> dead = RecipeIngredients.deadTags(json, isCut, emptied::contains);
            if (dead.isEmpty()) {
                continue;
            }
            String id = entry.getKey().toString();
            String needs = "#" + String.join(", #", dead);
            Decision rule = resolver.decide(ContentType.RECIPE, id);
            if (rule.action() == Decision.Action.KEEP) {
                core.warn("recipe " + id + " is kept by " + rule.rule().location() + " but needs " + needs + ", which the cuts emptied");
                continue;
            }
            change(core, "recipe dropped", id, "needs " + needs + " (emptied by cuts)");
            lostOutputs(json);
            if (core.applies()) {
                it.remove();
            }
        }
    }

    /** Rewrite only when every cut item the recipe uses asks for it; otherwise drop. */
    private static RecipeMode modeFor(ScalpelCore core, Set<String> refs) {
        for (String ref : refs) {
            Decision decision = core.resolver().cutState(ContentType.ITEM, ref);
            if (decision == null || decision.recipeMode(core.settings()) != RecipeMode.REWRITE) {
                return RecipeMode.DROP;
            }
        }
        return RecipeMode.REWRITE;
    }

    private static void lostOutputs(JsonElement recipe) {
        lostProducers.addAll(outputIds(recipe));
    }

    /** Item ids under a recipe's output keys: result(s), output(s), and mods' own such as Mekanism's secondaryOutput. */
    private static Set<String> outputIds(JsonElement recipe) {
        Set<String> ids = new HashSet<>();
        if (recipe.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : recipe.getAsJsonObject().entrySet()) {
                String key = entry.getKey().toLowerCase(java.util.Locale.ROOT);
                if (key.contains("result") || key.contains("output")) {
                    collectIds(entry.getValue(), ids);
                }
            }
        }
        return ids;
    }

    private static void collectIds(JsonElement element, Set<String> out) {
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            String id = JsonScrub.asId(element.getAsString());
            if (id != null) {
                out.add(id);
            }
        } else if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                collectIds(child, out);
            }
        } else if (element.isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : element.getAsJsonObject().entrySet()) {
                if (e.getKey().equals("item") || e.getKey().equals("id") || e.getValue().isJsonObject() || e.getValue().isJsonArray()) {
                    collectIds(e.getValue(), out);
                }
            }
        }
    }

    // ------------------------------------------------------------------ advancements

    private static void advancements(ScalpelCore core, Map<ResourceLocation, JsonElement> files) {
        Resolver resolver = core.resolver();
        Predicate<String> isCut = resolver::isCutAnywhere;
        boolean anyCut = anyRegistryCut(resolver);
        Set<String> dropped = new HashSet<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : files.entrySet()) {
            String id = entry.getKey().toString();
            Decision rule = resolver.decide(ContentType.ADVANCEMENT, id);
            if (rule.action() == Decision.Action.KEEP) {
                continue;
            }
            if (rule.action() == Decision.Action.REMOVE) {
                dropped.add(id);
                change(core, "advancement removed by rule", id, rule.rule().location());
                continue;
            }
            if (anyCut) {
                Set<String> refs = JsonScrub.findReferences(entry.getValue(), isCut);
                if (!refs.isEmpty()) {
                    dropped.add(id);
                    change(core, "advancement removed", id, "uses " + String.join(", ", refs));
                }
            }
        }
        // Children of a removed advancement would fail to load; take them out too and say why.
        boolean grew = !dropped.isEmpty();
        while (grew) {
            grew = false;
            for (Map.Entry<ResourceLocation, JsonElement> entry : files.entrySet()) {
                String id = entry.getKey().toString();
                if (dropped.contains(id) || !entry.getValue().isJsonObject()) {
                    continue;
                }
                JsonElement parent = entry.getValue().getAsJsonObject().get("parent");
                if (parent != null && parent.isJsonPrimitive() && dropped.contains(JsonScrub.asId(parent.getAsString()))) {
                    dropped.add(id);
                    change(core, "advancement removed", id, "its parent " + parent.getAsString() + " was removed");
                    grew = true;
                }
            }
        }
        if (core.applies()) {
            files.keySet().removeIf(key -> dropped.contains(key.toString()));
        }
        core.report().evaluated(Set.of(ContentType.ADVANCEMENT));
    }

    // ------------------------------------------------------------------ loot

    private static void lootTables(ScalpelCore core, Map<ResourceLocation, JsonElement> files) {
        Resolver resolver = core.resolver();
        Predicate<String> isCut = resolver::isCutAnywhere;
        boolean anyCut = anyRegistryCut(resolver);
        Iterator<Map.Entry<ResourceLocation, JsonElement>> it = files.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<ResourceLocation, JsonElement> entry = it.next();
            ResourceLocation key = entry.getKey();
            String id = key.toString();
            Decision rule = resolver.decide(ContentType.LOOT, id);
            if (rule.action() == Decision.Action.KEEP) {
                continue;
            }
            if (rule.action() == Decision.Action.REMOVE) {
                change(core, "loot table removed by rule", id, rule.rule().location());
                if (core.applies()) {
                    it.remove();
                }
                continue;
            }
            if (!anyCut) {
                continue;
            }
            String owner = ownerOfTable(key);
            ContentType ownerType = key.getPath().startsWith("blocks/") ? ContentType.BLOCK : ContentType.ENTITY;
            if (owner != null && resolver.isCut(ownerType, owner)) {
                change(core, "loot table removed", id, "belongs to cut " + owner);
                if (core.applies()) {
                    it.remove();
                }
                continue;
            }
            JsonElement json = core.applies() ? entry.getValue() : entry.getValue().deepCopy();
            JsonScrub.Result result = JsonScrub.scrub(json, isCut, LOOT);
            if (result.dropWhole()) {
                change(core, "loot table removed", id, "uses " + String.join(", ", result.hits()));
                if (core.applies()) {
                    it.remove();
                }
            } else if (result.changed()) {
                change(core, "loot entries removed", id, result.removed() + " for " + String.join(", ", result.hits()));
            }
        }
        core.report().evaluated(Set.of(ContentType.LOOT));
    }

    /** {@code examplemod:blocks/ruby_block} belongs to block {@code examplemod:ruby_block}. */
    private static String ownerOfTable(ResourceLocation table) {
        String path = table.getPath();
        for (String prefix : new String[]{"blocks/", "entities/"}) {
            if (path.startsWith(prefix) && path.indexOf('/', prefix.length()) < 0) {
                return table.getNamespace() + ":" + path.substring(prefix.length());
            }
        }
        return null;
    }

    private static void lootModifiers(ScalpelCore core, Map<ResourceLocation, JsonElement> files) {
        Resolver resolver = core.resolver();
        if (!anyRegistryCut(resolver)) {
            return;
        }
        Predicate<String> isCut = resolver::isCutAnywhere;
        List<String> dropped = new ArrayList<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : files.entrySet()) {
            String id = entry.getKey().toString();
            if (id.equals(LOOT_MODIFIER_INDEX)) {
                continue;
            }
            Set<String> refs = JsonScrub.findReferences(entry.getValue(), isCut);
            if (!refs.isEmpty()) {
                dropped.add(id);
                change(core, "loot modifier removed", id, "uses " + String.join(", ", refs));
            }
        }
        if (dropped.isEmpty() || !core.applies()) {
            return;
        }
        // Forge reads its index from the data packs rather than from this map, so it would still ask for these ids.
        // LootModifierManagerMixin keeps them out of its list.
        files.keySet().removeIf(key -> {
            if (dropped.contains(key.toString())) {
                droppedLootModifiers.add(key);
                return true;
            }
            return false;
        });
    }

    // ------------------------------------------------------------------ shared

    static boolean anyRegistryCut(Resolver resolver) {
        for (ContentType type : ContentType.REGISTRY_TYPES) {
            if (!resolver.cutIds(type).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    static void change(ScalpelCore core, String category, String target, String detail) {
        core.report().change(category, target, detail);
        core.log().detail((core.applies() ? "" : "[dry run] ") + category + ": " + target + (detail == null ? "" : " (" + detail + ")"));
    }
}
