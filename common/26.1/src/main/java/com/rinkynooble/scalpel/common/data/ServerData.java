package com.rinkynooble.scalpel.common.data;

import com.rinkynooble.scalpel.core.Cascade;
import com.rinkynooble.scalpel.core.ContentType;
import com.rinkynooble.scalpel.core.Decision;
import com.rinkynooble.scalpel.core.Resolver;
import com.rinkynooble.scalpel.core.ScalpelCore;
import com.rinkynooble.scalpel.core.rules.Rule;
import com.rinkynooble.scalpel.common.Scalpel;
import com.rinkynooble.scalpel.common.command.IdSuggestions;
import com.rinkynooble.scalpel.core.RecipeMode;
import com.rinkynooble.scalpel.common.registry.RegistryCutter;
import com.rinkynooble.scalpel.common.mixin.IngredientAccessor;
import com.rinkynooble.scalpel.common.mixin.RecipeManagerAccessor;
import com.rinkynooble.scalpel.common.mixin.ShapedRecipeAccessor;
import com.rinkynooble.scalpel.common.mixin.ShapelessRecipeAccessor;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.SingleItemRecipe;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Server data load lifecycle: resets data results when a load starts, runs a last pass over the built recipes
 * (catching recipes that scripts added, which never went through the JSON filter), and writes the report once
 * tags are bound.
 */
public final class ServerData {
    private ServerData() {
    }

    /** Called when a data load starts, before any file is read. */
    public static void onDataLoadStart() {
        DataFilter.beginDataLoad();
    }

    /** The recipe manager of the last data load. The first load happens before the server object exists. */
    private static volatile RecipeManager loadedRecipes;

    /**
     * The final recipe pass, from the HEAD of {@code RecipeManager#finalizeRecipeLoading}: on the main thread, after
     * every reload listener (and so after scripts), before the stonecutter list, recipe book and client sync are built.
     */
    public static void finalRecipePass(RecipeManager recipes) {
        loadedRecipes = recipes;
        finalRecipePass(recipes, ((RecipeManagerAccessor) recipes).scalpel$registries());
    }

    /** Called when the server is stopping: the report gets its last state. */
    public static void onServerStopping() {
        Scalpel.core().writeReport();
    }

    /** One ingredient that can no longer be filled: the cut item or emptied tag behind it. */
    private record Dead(int index, String cause, boolean viaTag) {
    }

    /**
     * The last word on recipes, run after every recipe (files, scripts, other mods) is built. The file pass already
     * handled recipes that name a cut item directly. This catches the rest: recipes added by scripts, and recipes
     * whose ingredient is a tag that the cuts left empty (Forge's own recipes use tags such as forge:string).
     * Shaped and shapeless crafting recipes can be rewritten here; anything else is dropped.
     */
    private static void finalRecipePass(RecipeManager recipes, HolderLookup.Provider access) {
        ScalpelCore core = Scalpel.core();
        Resolver resolver = core.resolver();
        boolean anyCut = !resolver.cutIds(ContentType.ITEM).isEmpty() || !TagFilter.emptiedItemTags().isEmpty();
        if (!anyCut && !resolver.rules().hasRulesFor(ContentType.RECIPE)) {
            return;
        }
        RecipeManagerAccessor manager = (RecipeManagerAccessor) recipes;
        ContextMap context = displayContext(access);
        List<RecipeHolder<?>> kept = new ArrayList<>();
        int changed = 0;
        for (RecipeHolder<?> holder : manager.scalpel$recipes().values()) {
            Recipe<?> recipe = holder.value();
            String id = holder.id().identifier().toString();
            boolean fromFile = DataFilter.wasScanned(id);
            Decision rule = resolver.decide(ContentType.RECIPE, id);
            if (rule.action() == Decision.Action.KEEP) {
                kept.add(holder);
                continue;
            }
            String reason = null;
            String detail = null;
            if (rule.action() == Decision.Action.REMOVE) {
                if (!fromFile || core.applies()) {
                    reason = "recipe removed by rule";
                    detail = rule.rule().location();
                }
            } else if (anyCut) {
                ItemStack result = safeResult(recipe, context);
                String special = result == null || result.isEmpty() ? cutSpecialProducts(recipe, resolver) : null;
                if (result != null && !result.isEmpty() && RegistryCutter.isCutItem(result.getItem())) {
                    if (!fromFile || core.applies()) {
                        reason = "recipe dropped";
                        detail = "makes " + RegistryCutter.itemId(result.getItem());
                    }
                } else if (special != null) {
                    // The file pass can't see these (their files name no item), so they're reported here even in a dry run.
                    reason = "recipe dropped";
                    detail = "makes " + special + " (a special recipe, matched by its type)";
                } else {
                    List<Dead> dead = deadIngredients(recipe);
                    if (!core.applies() && fromFile) {
                        dead.removeIf(d -> !d.viaTag());
                    }
                    if (!dead.isEmpty()) {
                        String causes = dead.stream().map(Dead::cause).distinct().collect(Collectors.joining(", "));
                        Recipe<?> rewritten = rewriteWanted(core, dead) ? rewrite(recipe, dead) : null;
                        if (rewritten != null) {
                            DataFilter.change(core, "recipe rewritten", id, "without " + causes);
                            kept.add(core.applies() ? rehold(holder, rewritten) : holder);
                            changed++;
                            continue;
                        }
                        reason = "recipe dropped";
                        detail = "needs " + causes;
                    }
                }
            }
            if (reason == null) {
                kept.add(holder);
                continue;
            }
            DataFilter.change(core, reason, id, detail + (fromFile ? "" : " (added by a script or mod code)"));
            ItemStack result = safeResult(recipe, context);
            if (result != null && !result.isEmpty()) {
                DataFilter.lostProducers().add(RegistryCutter.itemId(result.getItem()));
            }
            changed++;
        }
        if (changed > 0 && core.applies()) {
            manager.scalpel$setRecipes(RecipeMap.create(kept));
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static RecipeHolder<?> rehold(RecipeHolder<?> holder, Recipe<?> recipe) {
        return new RecipeHolder(holder.id(), recipe);
    }

    /** What recipe displays need to show tag and registry based slots. */
    private static ContextMap displayContext(HolderLookup.Provider access) {
        return new ContextMap.Builder().withParameter(SlotDisplayContext.REGISTRIES, access).create(SlotDisplayContext.CONTEXT);
    }

    /**
     * What vanilla's special crafting recipes make, by serializer, for those that show no result (the game works it
     * out from the grid). 26.1 gives every vanilla special recipe a result in its file (tipped arrows are
     * crafting_imbue, armor dyeing crafting_dye, map cloning crafting_transmute), so the file pass catches them; this
     * stays for any that still shows none.
     */
    private static final Map<String, List<String>> SPECIAL_PRODUCTS = specialProducts();

    private static Map<String, List<String>> specialProducts() {
        Map<String, List<String>> products = new HashMap<>();
        products.put("minecraft:crafting_special_firework_star_fade", List.of("minecraft:firework_star"));
        products.put("minecraft:crafting_special_bookcloning", List.of("minecraft:written_book"));
        products.put("minecraft:crafting_special_shielddecoration", List.of("minecraft:shield"));
        products.put("minecraft:crafting_decorated_pot", List.of("minecraft:decorated_pot"));
        products.put("minecraft:crafting_special_mapextending", List.of("minecraft:filled_map"));
        List<String> banners = new ArrayList<>();
        for (DyeColor color : DyeColor.values()) {
            banners.add("minecraft:" + color.getName() + "_banner");
        }
        products.put("minecraft:crafting_special_bannerduplicate", banners);
        return products;
    }

    /** The products of a special recipe when every one of them is cut, else null. */
    private static String cutSpecialProducts(Recipe<?> recipe, Resolver resolver) {
        Identifier serializer = BuiltInRegistries.RECIPE_SERIALIZER.getKey(recipe.getSerializer());
        List<String> products = serializer == null ? null : SPECIAL_PRODUCTS.get(serializer.toString());
        if (products == null || !products.stream().allMatch(id -> resolver.isCut(ContentType.ITEM, id))) {
            return null;
        }
        return products.size() == 1 ? products.get(0) : String.join(", ", products);
    }

    /**
     * Ingredients made only of cut items or tags the cuts emptied. Shaped slots are numbered by grid position, shapeless
     * ingredients by list position. Other vanilla and mod recipe types are read through their placement info (read
     * only: they can't be rewritten). Custom (non-vanilla) ingredients are not inspected.
     */
    private static List<Dead> deadIngredients(Recipe<?> recipe) {
        List<Dead> dead = new ArrayList<>();
        try {
            if (recipe instanceof ShapedRecipe shaped) {
                List<Optional<Ingredient>> slots = shaped.getIngredients();
                for (int i = 0; i < slots.size(); i++) {
                    int index = i;
                    slots.get(i).map(ingredient -> deadCause(ingredient, index)).ifPresent(dead::add);
                }
            } else if (recipe instanceof ShapelessRecipe shapeless) {
                addDead(((ShapelessRecipeAccessor) shapeless).scalpel$ingredients(), dead);
            } else if (recipe instanceof SingleItemRecipe single) {
                addDead(List.of(single.input()), dead);
            } else {
                addDead(recipe.placementInfo().ingredients(), dead);
            }
        } catch (RuntimeException e) {
            dead.clear();
        }
        return dead;
    }

    private static void addDead(List<Ingredient> ingredients, List<Dead> dead) {
        for (int i = 0; i < ingredients.size(); i++) {
            Dead d = deadCause(ingredients.get(i), i);
            if (d != null) {
                dead.add(d);
            }
        }
    }

    /** Why an ingredient can't be filled any more, or null when it still can (or Scalpel can't read it). */
    private static Dead deadCause(Ingredient ingredient, int index) {
        if (!Scalpel.platform().isVanillaIngredient(ingredient)) {
            return null;
        }
        HolderSet<Item> values = ((IngredientAccessor) (Object) ingredient).scalpel$values();
        Optional<TagKey<Item>> tag = values.unwrapKey();
        if (tag.isPresent()) {
            String id = tag.get().location().toString();
            return TagFilter.emptiedItemTags().contains(id) ? new Dead(index, "#" + id, true) : null;
        }
        String cause = null;
        for (Holder<Item> item : values) {
            if (!RegistryCutter.isCutItem(item.value())) {
                return null;
            }
            cause = RegistryCutter.itemId(item.value());
        }
        return cause == null ? null : new Dead(index, cause, false);
    }

    /**
     * Rewrite only when every cut item involved asks for it. For an emptied tag, those are the cut items the tag
     * lost; a tag emptied by a rule follows the global setting.
     */
    private static boolean rewriteWanted(ScalpelCore core, List<Dead> dead) {
        for (Dead d : dead) {
            Set<String> items = d.viaTag() ? TagFilter.cutMembers(d.cause().substring(1)) : Set.of(d.cause());
            if (items.isEmpty() && core.settings().ingredientMode() != RecipeMode.REWRITE) {
                return false;
            }
            for (String item : items) {
                Decision decision = core.resolver().cutState(ContentType.ITEM, item);
                RecipeMode mode = decision == null ? core.settings().ingredientMode() : decision.recipeMode(core.settings());
                if (mode != RecipeMode.REWRITE) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Shaped: the dead slots become blank. Shapeless: the dead ingredients are taken out. Returns a new recipe, or null
     * (drop the recipe) for other recipe types, subclasses (a mod's own behaviour would be lost), or when nothing would
     * be left. Recipes cache their placement info, so a changed recipe is always a new object.
     */
    private static Recipe<?> rewrite(Recipe<?> recipe, List<Dead> dead) {
        Set<Integer> deadSlots = dead.stream().map(Dead::index).collect(Collectors.toSet());
        if (recipe.getClass() == ShapedRecipe.class) {
            ShapedRecipe shaped = (ShapedRecipe) recipe;
            List<Optional<Ingredient>> slots = new ArrayList<>(shaped.getIngredients());
            long left = slots.stream().filter(Optional::isPresent).count() - deadSlots.size();
            if (left <= 0) {
                return null;
            }
            for (int index : deadSlots) {
                slots.set(index, Optional.empty());
            }
            ShapedRecipePattern pattern = new ShapedRecipePattern(shaped.getWidth(), shaped.getHeight(), slots, Optional.empty());
            // 26.1: results are ItemStackTemplates, and the book fields come in two records.
            return new ShapedRecipe(new Recipe.CommonInfo(shaped.showNotification()),
                    new CraftingRecipe.CraftingBookInfo(shaped.category(), shaped.group()), pattern,
                    ((ShapedRecipeAccessor) shaped).scalpel$result());
        }
        if (recipe.getClass() == ShapelessRecipe.class) {
            ShapelessRecipe shapeless = (ShapelessRecipe) recipe;
            List<Ingredient> remaining = new ArrayList<>();
            List<Ingredient> list = ((ShapelessRecipeAccessor) shapeless).scalpel$ingredients();
            for (int i = 0; i < list.size(); i++) {
                if (!deadSlots.contains(i)) {
                    remaining.add(list.get(i));
                }
            }
            if (remaining.isEmpty()) {
                return null;
            }
            return new ShapelessRecipe(new Recipe.CommonInfo(shapeless.showNotification()),
                    new CraftingRecipe.CraftingBookInfo(shapeless.category(), shapeless.group()),
                    ((ShapelessRecipeAccessor) shapeless).scalpel$result(), remaining);
        }
        return null;
    }

    /** A recipe's main product, read from its first display (1.21.2 recipes have no result getter). */
    private static ItemStack safeResult(Recipe<?> recipe, ContextMap context) {
        try {
            List<RecipeDisplay> displays = recipe.display();
            return displays.isEmpty() ? null : displays.get(0).result().resolveForFirstStack(context);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Every product a recipe shows, for the find command. */
    public static Set<String> productsOf(Recipe<?> recipe, HolderLookup.Provider access) {
        return results(recipe, displayContext(access));
    }

    /** Every product a recipe shows (all of its displays). */
    private static Set<String> results(Recipe<?> recipe, ContextMap context) {
        Set<String> ids = new LinkedHashSet<>();
        try {
            for (RecipeDisplay display : recipe.display()) {
                for (ItemStack stack : display.result().resolveForStacks(context)) {
                    if (!stack.isEmpty()) {
                        ids.add(RegistryCutter.itemId(stack.getItem()));
                    }
                }
            }
        } catch (RuntimeException e) {
            // A mod's display that needs a level: no products known.
        }
        return ids;
    }

    /**
     * Called once the server's data load is complete: at the TAIL of {@code RecipeManager#finalizeRecipeLoading}, after
     * tags are bound and after the final recipe pass (not for tags a client receives).
     */
    public static void afterDataLoad() {
        ScalpelCore core = Scalpel.core();
        IdSuggestions.dataChanged();
        RecipeManager recipes = loadedRecipes;
        if (recipes != null && core.settings().cascadeDepth() > 0 && !DataFilter.lostProducers().isEmpty()) {
            core.report().cascade(cascade(core, recipes), core.settings().cascadeDepth());
        }
        core.report().evaluated(EnumSet.of(ContentType.RECIPE, ContentType.LOOT, ContentType.ADVANCEMENT, ContentType.TAG));
        for (Rule rule : core.report().unmatchedRules(core.rules().rules())) {
            if (rule.types().stream().noneMatch(ContentType::isRegistry) || rule.types().contains(ContentType.ENCHANTMENT)) {
                core.warn("rule matched nothing: " + rule.canonical() + " (" + rule.location() + ")");
            }
        }
        long changes = core.report().changes().size();
        core.log().info("Data filtered: " + changes + " changes" + (core.applies() ? "" : " (dry run, nothing applied)")
                + ". Report: " + core.reportFile());
        core.writeReport();
    }

    private static List<Set<String>> cascade(ScalpelCore core, RecipeManager recipes) {
        RecipeManagerAccessor manager = (RecipeManagerAccessor) recipes;
        ContextMap context = displayContext(manager.scalpel$registries());
        List<Cascade.RecipeInfo> infos = new ArrayList<>();
        for (RecipeHolder<?> holder : manager.scalpel$recipes().values()) {
            Recipe<?> recipe = holder.value();
            String recipeId = holder.id().identifier().toString();
            Set<String> outputs = results(recipe, context);
            // A mod recipe with no display (Farmer's Delight's cutting board) still names its outputs in its file.
            outputs.addAll(DataFilter.outputsNamed(recipeId));
            if (outputs.isEmpty()) {
                continue;
            }
            List<Set<String>> ingredients = new ArrayList<>();
            try {
                for (Ingredient ingredient : recipe.placementInfo().ingredients()) {
                    Set<String> ids = new LinkedHashSet<>();
                    ingredient.items().forEach(item -> ids.add(RegistryCutter.itemId(item.value())));
                    if (!ids.isEmpty()) {
                        ingredients.add(ids);
                    }
                }
            } catch (RuntimeException e) {
                ingredients.clear();
            }
            infos.add(new Cascade.RecipeInfo(recipeId, outputs, ingredients));
        }
        Set<String> lost = new HashSet<>();
        for (String id : DataFilter.lostProducers()) {
            Identifier location = Identifier.tryParse(id);
            if (location != null && BuiltInRegistries.ITEM.containsKey(location)) {
                lost.add(id);
            }
        }
        Set<String> cut = new HashSet<>(core.resolver().cutIds(ContentType.ITEM));
        return Cascade.compute(infos, lost, cut, core.settings().cascadeDepth());
    }
}
