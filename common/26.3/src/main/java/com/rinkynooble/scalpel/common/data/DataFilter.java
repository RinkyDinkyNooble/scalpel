package com.rinkynooble.scalpel.common.data;

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
import com.rinkynooble.scalpel.common.Scalpel;
import com.rinkynooble.scalpel.common.registry.RegistryCutter;
import com.google.gson.JsonParser;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Reader;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import com.google.gson.JsonArray;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Filters data files right after they are read and before anything parses them: recipes, advancements,
 * loot tables and loot modifiers. Anything dropped here is never parsed, so a removed id never produces
 * an "unknown item" error.
 */
public final class DataFilter {
    /**
     * A cut id inside a loot condition or function takes out the entry that owns it, never just the condition or
     * function. 26.3 gives entries one {@code condition} and one {@code modifier} (which may be an inline list);
     * {@code functions} is a sequence's list.
     */
    private static final JsonScrub.Policy LOOT = new JsonScrub.Policy(JsonScrub.EmptyArrays.KEEP, Set.of("conditions", "functions", "modifier"));
    /** The loaders' loot modifier index files, which list modifiers rather than being one. */
    private static final Set<String> LOOT_MODIFIER_INDEXES = Set.of("forge:global_loot_modifiers", "neoforge:global_loot_modifiers");

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
    private static final Set<Identifier> droppedLootModifiers = ConcurrentHashMap.newKeySet();

    private DataFilter() {
    }

    public static void beginDataLoad() {
        synchronized (DataFilter.class) {
            reloadableFor = new WeakReference<>(null);
            reloadableFiles = Map.of();
        }
        lostProducers.clear();
        scannedRecipes.clear();
        moreOutputs.clear();
        droppedLootModifiers.clear();
        synchronized (DataFilter.class) {
            tradesFor = new WeakReference<>(null);
            droppedTrades = Set.of();
        }
        TagFilter.reset();
        GeneratedLoot.reset();
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

    public static boolean isDroppedLootModifier(Identifier id) {
        return droppedLootModifiers.contains(id);
    }

    /** The data folders {@link #afterScan} reads. Other listers (client assets among them) pass through untouched. */
    private static final Set<String> FOLDERS = Set.of("recipe", "advancement", "loot_table", "loot_modifiers");

    /**
     * Called with the file list of the recipe, advancement and loot table registries ({@code RegistryFiles}, 26.3) and
     * of every directory a JSON reload listener reads (loot modifiers). Reads the JSON of each file, runs
     * {@link #afterScan} over all of them as before, and returns the list without dropped files and with changed files
     * replaced by their new JSON. A file that doesn't parse is left for the game to report.
     */
    public static Map<Identifier, Resource> filterFiles(FileToIdConverter lister, Map<Identifier, Resource> files) {
        String directory = lister.prefix();
        if (!FOLDERS.contains(directory) || files.isEmpty()) {
            return files;
        }
        DataFiles data = new DataFiles(lister, files);
        afterScan(directory, data.json);
        return data.apply(files);
    }

    /**
     * One folder's files as JSON (file id to JSON), with a copy of each as read, so what the filters changed or took
     * out can be handed back as a file list. A file that doesn't parse is left as it is, for the game to report.
     */
    private static final class DataFiles {
        final FileToIdConverter lister;
        final Map<Identifier, JsonElement> json = new LinkedHashMap<>();
        final Map<Identifier, JsonElement> before = new HashMap<>();

        DataFiles(FileToIdConverter lister, Map<Identifier, Resource> files) {
            this.lister = lister;
            for (Map.Entry<Identifier, Resource> entry : files.entrySet()) {
                Identifier id = lister.fileToId(entry.getKey());
                try (Reader reader = entry.getValue().openAsReader()) {
                    JsonElement element = JsonParser.parseReader(reader);
                    json.put(id, element);
                    before.put(id, element.deepCopy());
                } catch (IOException | RuntimeException e) {
                    // The game reads it again and logs why it can't.
                }
            }
        }

        /** Ids of the files the filters took out. */
        Set<String> gone() {
            Set<String> gone = new HashSet<>();
            for (Identifier id : before.keySet()) {
                if (!json.containsKey(id)) {
                    gone.add(id.toString());
                }
            }
            return gone;
        }

        /** The file list without the files taken out, and with changed files handed back as their new JSON. */
        Map<Identifier, Resource> apply(Map<Identifier, Resource> files) {
            Map<Identifier, Resource> out = new LinkedHashMap<>();
            for (Map.Entry<Identifier, Resource> entry : files.entrySet()) {
                Identifier id = lister.fileToId(entry.getKey());
                JsonElement original = before.get(id);
                JsonElement now = json.get(id);
                if (original == null || now != null && now.equals(original)) {
                    out.put(entry.getKey(), entry.getValue());
                } else if (now != null) {
                    byte[] bytes = now.toString().getBytes(StandardCharsets.UTF_8);
                    out.put(entry.getKey(), new Resource(entry.getValue().source(), () -> new ByteArrayInputStream(bytes)));
                }
            }
            return out;
        }
    }

    // ------------------------------------------------------------------ recipes, advancements, loot tables (registries since 26.3)

    /** The resource manager {@link #reloadableFiles} was worked out for: one per data load. */
    private static WeakReference<ResourceManager> reloadableFor = new WeakReference<>(null);
    private static Map<ResourceKey<? extends Registry<?>>, DataFiles> reloadableFiles = Map.of();

    /**
     * The file list of the recipe, advancement or loot table registry. Since 26.3 these are datapack registries,
     * loaded at the same time, and they name each other as registry entries: an advancement's criteria name recipes
     * and loot tables, its rewards name loot tables, a loot table names other loot tables. An entry that names one that
     * was left out stops the whole data load ("Unbound values in registry"). So all three are decided once per data
     * load, recipes and loot tables first, and what names a left-out one is fixed before any file is read.
     */
    public static Map<Identifier, Resource> filterReloadableFiles(ResourceKey<? extends Registry<?>> registry,
                                                                  Map<Identifier, Resource> files, ResourceManager manager) {
        DataFiles data = reloadableFiles(manager).get(registry);
        return data == null ? files : data.apply(files);
    }

    private static synchronized Map<ResourceKey<? extends Registry<?>>, DataFiles> reloadableFiles(ResourceManager manager) {
        if (reloadableFor.get() == manager) {
            return reloadableFiles;
        }
        ScalpelCore core = Scalpel.core();
        DataFiles recipes = read(Registries.RECIPE, manager);
        DataFiles loot = read(Registries.LOOT_TABLE, manager);
        DataFiles advancements = read(Registries.ADVANCEMENT, manager);
        recipes(core, recipes.json);
        lootTables(core, loot.json);
        Set<String> goneRecipes = recipes.gone();
        Set<String> goneLoot = loot.gone();
        if (!goneLoot.isEmpty()) {
            lootReferences(core, loot.json, goneLoot);
        }
        advancements(core, advancements.json, goneRecipes, goneLoot);
        reloadableFiles = Map.of(Registries.RECIPE, recipes, Registries.LOOT_TABLE, loot, Registries.ADVANCEMENT, advancements);
        reloadableFor = new WeakReference<>(manager);
        return reloadableFiles;
    }

    private static DataFiles read(ResourceKey<? extends Registry<?>> registry, ResourceManager manager) {
        FileToIdConverter lister = FileToIdConverter.registry(registry);
        return new DataFiles(lister, lister.listMatchingResources(manager));
    }

    /** Called with every directory scanned, as file id to JSON (see {@link #filterFiles}). */
    public static void afterScan(String directory, Map<Identifier, JsonElement> files) {
        ScalpelCore core = Scalpel.core();
        switch (directory) {
            case "recipe" -> recipes(core, files);
            case "advancement" -> advancements(core, files);
            case "loot_table" -> lootTables(core, files);
            case "loot_modifiers" -> lootModifiers(core, files);
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ recipes

    private static void recipes(ScalpelCore core, Map<Identifier, JsonElement> files) {
        for (Identifier id : files.keySet()) {
            scannedRecipes.add(id.toString());
        }
        Resolver resolver = core.resolver();
        boolean anyCut = !resolver.cutIds(ContentType.ITEM).isEmpty();
        if (!anyCut && !resolver.rules().hasRulesFor(ContentType.RECIPE)) {
            core.report().evaluated(Set.of(ContentType.RECIPE));
            return;
        }
        if (core.settings().cascadeDepth() > 0) {
            for (Map.Entry<Identifier, JsonElement> entry : files.entrySet()) {
                Set<String> outputs = outputIds(entry.getValue());
                if (outputs.size() > 1) {
                    moreOutputs.put(entry.getKey().toString(), outputs);
                }
            }
        }
        Predicate<String> isCut = id -> resolver.isCut(ContentType.ITEM, id);
        Iterator<Map.Entry<Identifier, JsonElement>> it = files.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Identifier, JsonElement> entry = it.next();
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
        // Since 1.21.2 tags load before any reload listener, so they are known here already.
        beforeRecipeApply(files);
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
    public static void beforeRecipeApply(Map<Identifier, JsonElement> recipes) {
        Set<String> emptied = TagFilter.emptiedItemTags();
        if (emptied.isEmpty()) {
            return;
        }
        ScalpelCore core = Scalpel.core();
        Resolver resolver = core.resolver();
        Predicate<String> isCut = id -> resolver.isCut(ContentType.ITEM, id);
        Iterator<Map.Entry<Identifier, JsonElement>> it = recipes.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Identifier, JsonElement> entry = it.next();
            if (!entry.getValue().isJsonObject()) {
                continue;
            }
            JsonObject json = entry.getValue().getAsJsonObject();
            String type = json.has("type") && json.get("type").isJsonPrimitive() ? JsonScrub.asId(json.get("type").getAsString()) : null;
            if (type == null || READ_BY_FINAL_PASS.contains(type)) {
                continue;
            }
            // 1.21.2+ writes tag ingredients as "#ns:tag" strings.
            Set<String> dead = RecipeIngredients.deadTags(json, isCut, emptied::contains, true);
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

    private static void advancements(ScalpelCore core, Map<Identifier, JsonElement> files) {
        advancements(core, files, Set.of(), Set.of());
    }

    /**
     * @param goneRecipes recipes left out of this load (26.3: criteria name them as registry entries)
     * @param goneLoot    loot tables left out of this load (criteria and rewards name them as registry entries)
     */
    private static void advancements(ScalpelCore core, Map<Identifier, JsonElement> files, Set<String> goneRecipes, Set<String> goneLoot) {
        Resolver resolver = core.resolver();
        Predicate<String> isCut = resolver::isCutAnywhere;
        boolean anyCut = anyRegistryCut(resolver);
        Set<String> dropped = new HashSet<>();
        for (Map.Entry<Identifier, JsonElement> entry : files.entrySet()) {
            String id = entry.getKey().toString();
            String missing = goneRecipes.isEmpty() && goneLoot.isEmpty() ? null
                    : AdvancementReferences.fix(entry.getValue(), goneRecipes, goneLoot);
            if (missing != null) {
                // It can't load at all now, whatever a rule says.
                Decision kept = resolver.decide(ContentType.ADVANCEMENT, id);
                if (kept.action() == Decision.Action.KEEP) {
                    core.warn("advancement " + id + " is kept by " + kept.rule().location() + " but needs " + missing + ", so it can't load");
                }
                dropped.add(id);
                change(core, "advancement removed", id, "needs " + missing);
                continue;
            }
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
            for (Map.Entry<Identifier, JsonElement> entry : files.entrySet()) {
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

    private static void lootTables(ScalpelCore core, Map<Identifier, JsonElement> files) {
        Resolver resolver = core.resolver();
        Predicate<String> isCut = resolver::isCutAnywhere;
        boolean anyCut = anyRegistryCut(resolver);
        Iterator<Map.Entry<Identifier, JsonElement>> it = files.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Identifier, JsonElement> entry = it.next();
            Identifier key = entry.getKey();
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
            Set<String> enchantments = TypedLootEnchantments.strip(json, resolver::isRemovedEnchantment);
            if (!enchantments.isEmpty()) {
                change(core, "loot enchantments removed", id, String.join(", ", enchantments));
            }
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

    /**
     * Loot tables that name a left-out table (a {@code loot_table} entry, a {@code set_loot_table} function) lose that
     * entry or function (26.3: they name it as a registry entry, which would stop the load).
     */
    private static void lootReferences(ScalpelCore core, Map<Identifier, JsonElement> files, Set<String> goneLoot) {
        for (Map.Entry<Identifier, JsonElement> entry : files.entrySet()) {
            Set<String> hits = new TreeSet<>();
            int removed = LootReferences.strip(entry.getValue(), goneLoot, hits);
            if (removed > 0) {
                change(core, "loot entries removed", entry.getKey().toString(), removed + " for removed loot table " + String.join(", ", hits));
            }
        }
    }

    /** {@code examplemod:blocks/ruby_block} belongs to block {@code examplemod:ruby_block}. */
    private static String ownerOfTable(Identifier table) {
        String path = table.getPath();
        for (String prefix : new String[]{"blocks/", "entities/"}) {
            if (path.startsWith(prefix) && path.indexOf('/', prefix.length()) < 0) {
                return table.getNamespace() + ":" + path.substring(prefix.length());
            }
        }
        return null;
    }

    private static void lootModifiers(ScalpelCore core, Map<Identifier, JsonElement> files) {
        Resolver resolver = core.resolver();
        if (!anyRegistryCut(resolver)) {
            return;
        }
        Predicate<String> isCut = resolver::isCutAnywhere;
        List<String> dropped = new ArrayList<>();
        for (Map.Entry<Identifier, JsonElement> entry : files.entrySet()) {
            String id = entry.getKey().toString();
            if (LOOT_MODIFIER_INDEXES.contains(id)) {
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

    // ------------------------------------------------------------------ villager trades (data since 26.1)

    /** The resource manager {@link #droppedTrades} was worked out for: one per data load. */
    private static WeakReference<ResourceManager> tradesFor = new WeakReference<>(null);
    private static Set<Identifier> droppedTrades = Set.of();

    /**
     * Villager and wandering trader trades are data since 26.1: one file per trade ({@code villager_trade}), grouped by
     * trade sets ({@code trade_set}) through trade tags or lists. A trade whose file names a cut item or a cut
     * enchantment anywhere (what it wants, what it gives, its item modifiers) is left out, as the 1.21 ports skipped such
     * offers; hidden items stay. Worked out once per data load from the files themselves, because 26.1 loads every
     * datapack registry at the same time and trade sets and trade tags need the answer too. Empty in a dry run (the
     * report still lists them).
     */
    public static synchronized Set<Identifier> droppedTrades(ResourceManager manager) {
        if (tradesFor.get() == manager) {
            return droppedTrades;
        }
        tradesFor = new WeakReference<>(manager);
        droppedTrades = Set.of();
        ScalpelCore core = Scalpel.core();
        if (core.rules().isEmpty()) {
            return droppedTrades;
        }
        Resolver resolver = core.resolver();
        // The enchantment registry may not have loaded yet: decide its ids now (the same decisions it makes itself).
        FileToIdConverter enchantments = FileToIdConverter.registry(Registries.ENCHANTMENT);
        for (Identifier file : enchantments.listMatchingResources(manager).keySet()) {
            RegistryCutter.decideData(ContentType.ENCHANTMENT, enchantments.fileToId(file));
        }
        if (resolver.cutIds(ContentType.ITEM).isEmpty() && resolver.cutIds(ContentType.ENCHANTMENT).isEmpty()) {
            return droppedTrades;
        }
        Predicate<String> isCut = id -> resolver.isCut(ContentType.ITEM, id) || resolver.isCut(ContentType.ENCHANTMENT, id);
        FileToIdConverter trades = FileToIdConverter.registry(Registries.VILLAGER_TRADE);
        Set<Identifier> dropped = new HashSet<>();
        for (Map.Entry<Identifier, Resource> file : trades.listMatchingResources(manager).entrySet()) {
            JsonElement json;
            try (Reader reader = file.getValue().openAsReader()) {
                json = JsonParser.parseReader(reader);
            } catch (IOException | RuntimeException e) {
                continue; // The game reports a file it can't read.
            }
            Set<String> refs = JsonScrub.findReferences(json, isCut);
            if (!refs.isEmpty()) {
                Identifier id = trades.fileToId(file.getKey());
                change(core, "trade removed", id.toString(), "uses " + String.join(", ", refs));
                dropped.add(id);
            }
        }
        if (core.applies()) {
            droppedTrades = Set.copyOf(dropped);
        }
        return droppedTrades;
    }

    /** The villager_trade file list: trades that name cut content are left out. */
    public static Map<Identifier, Resource> filterTradeFiles(FileToIdConverter lister, Map<Identifier, Resource> files,
                                                             ResourceManager manager) {
        Set<Identifier> dropped = droppedTrades(manager);
        if (dropped.isEmpty()) {
            return files;
        }
        Map<Identifier, Resource> kept = new LinkedHashMap<>(files);
        kept.keySet().removeIf(file -> dropped.contains(lister.fileToId(file)));
        return kept;
    }

    /**
     * The trade_set file list: a set that lists a left-out trade by id loses it (a set that names a trade tag is
     * handled by the tag, see {@link TagFilter}). A trade set that names a missing trade would stop the world loading.
     */
    public static Map<Identifier, Resource> filterTradeSetFiles(FileToIdConverter lister, Map<Identifier, Resource> files,
                                                                ResourceManager manager) {
        Set<Identifier> dropped = droppedTrades(manager);
        if (dropped.isEmpty()) {
            return files;
        }
        ScalpelCore core = Scalpel.core();
        Predicate<String> gone = ref -> {
            Identifier id = Identifier.tryParse(ref);
            return id != null && dropped.contains(id);
        };
        Map<Identifier, Resource> out = new LinkedHashMap<>();
        for (Map.Entry<Identifier, Resource> file : files.entrySet()) {
            JsonElement json;
            try (Reader reader = file.getValue().openAsReader()) {
                json = JsonParser.parseReader(reader);
            } catch (IOException | RuntimeException e) {
                out.put(file.getKey(), file.getValue());
                continue;
            }
            JsonElement trades = json.isJsonObject() ? json.getAsJsonObject().get("trades") : null;
            List<String> removed = new ArrayList<>();
            if (trades != null && trades.isJsonPrimitive() && gone.test(trades.getAsString())) {
                removed.add(trades.getAsString());
                json.getAsJsonObject().add("trades", new JsonArray());
            } else if (trades != null && trades.isJsonArray()) {
                Iterator<JsonElement> it = trades.getAsJsonArray().iterator();
                while (it.hasNext()) {
                    JsonElement entry = it.next();
                    if (entry.isJsonPrimitive() && gone.test(entry.getAsString())) {
                        removed.add(entry.getAsString());
                        it.remove();
                    }
                }
            }
            if (removed.isEmpty()) {
                out.put(file.getKey(), file.getValue());
                continue;
            }
            change(core, "trade set entries removed", lister.fileToId(file.getKey()).toString(), String.join(", ", removed));
            byte[] bytes = json.toString().getBytes(StandardCharsets.UTF_8);
            out.put(file.getKey(), new Resource(file.getValue().source(), () -> new ByteArrayInputStream(bytes)));
        }
        return out;
    }

    // ------------------------------------------------------------------ shared

    public static boolean anyRegistryCut(Resolver resolver) {
        for (ContentType type : ContentType.REGISTRY_TYPES) {
            if (!resolver.cutIds(type).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public static void change(ScalpelCore core, String category, String target, String detail) {
        core.report().change(category, target, detail);
        core.log().detail((core.applies() ? "" : "[dry run] ") + category + ": " + target + (detail == null ? "" : " (" + detail + ")"));
    }
}
