package com.rinkynooble.scalpel.forge.data;

import com.rinkynooble.scalpel.core.Cascade;
import com.rinkynooble.scalpel.core.ContentType;
import com.rinkynooble.scalpel.core.Decision;
import com.rinkynooble.scalpel.core.Resolver;
import com.rinkynooble.scalpel.core.ScalpelCore;
import com.rinkynooble.scalpel.core.rules.Rule;
import com.rinkynooble.scalpel.forge.Scalpel;
import com.rinkynooble.scalpel.core.RecipeMode;
import com.rinkynooble.scalpel.forge.mixin.IngredientAccessor;
import com.rinkynooble.scalpel.forge.mixin.TagValueAccessor;
import com.rinkynooble.scalpel.forge.registry.RegistryCutter;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
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

    public static void register(IEventBus forgeBus) {
        forgeBus.addListener(EventPriority.HIGHEST, ServerData::onDataLoadStart);
        forgeBus.addListener(EventPriority.LOWEST, ServerData::addFinalRecipePass);
        forgeBus.addListener(ServerData::onTagsUpdated);
        forgeBus.addListener((ServerStoppingEvent event) -> Scalpel.core().writeReport());
    }

    private static void onDataLoadStart(AddReloadListenerEvent event) {
        DataFilter.beginDataLoad();
    }

    private static void addFinalRecipePass(AddReloadListenerEvent event) {
        ReloadableServerResources resources = event.getServerResources();
        RegistryAccess access = event.getRegistryAccess();
        event.addListener((PreparableReloadListener) (barrier, manager, prepProfiler, applyProfiler, background, main) ->
                barrier.wait(null).thenRunAsync(() -> finalRecipePass(resources.getRecipeManager(), access), main));
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
    private static void finalRecipePass(RecipeManager recipes, RegistryAccess access) {
        ScalpelCore core = Scalpel.core();
        Resolver resolver = core.resolver();
        boolean anyCut = !resolver.cutIds(ContentType.ITEM).isEmpty() || !TagFilter.emptiedItemTags().isEmpty();
        if (!anyCut && !resolver.rules().hasRulesFor(ContentType.RECIPE)) {
            return;
        }
        List<Recipe<?>> kept = new ArrayList<>();
        int dropped = 0;
        for (Recipe<?> recipe : recipes.getRecipes()) {
            String id = recipe.getId().toString();
            boolean fromFile = DataFilter.wasScanned(id);
            Decision rule = resolver.decide(ContentType.RECIPE, id);
            if (rule.action() == Decision.Action.KEEP) {
                kept.add(recipe);
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
                ItemStack result = safeResult(recipe, access);
                if (result != null && !result.isEmpty() && RegistryCutter.isCutItem(result.getItem())) {
                    if (!fromFile || core.applies()) {
                        reason = "recipe dropped";
                        detail = "makes " + RegistryCutter.itemId(result.getItem());
                    }
                } else {
                    List<Dead> dead = deadIngredients(recipe);
                    if (!core.applies() && fromFile) {
                        dead.removeIf(d -> !d.viaTag());
                    }
                    if (!dead.isEmpty()) {
                        String causes = dead.stream().map(Dead::cause).distinct().collect(Collectors.joining(", "));
                        if (rewriteWanted(core, dead) && rewrite(recipe, dead, core.applies())) {
                            DataFilter.change(core, "recipe rewritten", id, "without " + causes);
                            kept.add(recipe);
                            continue;
                        }
                        reason = "recipe dropped";
                        detail = "needs " + causes;
                    }
                }
            }
            if (reason == null) {
                kept.add(recipe);
                continue;
            }
            DataFilter.change(core, reason, id, detail + (fromFile ? "" : " (added by a script or mod code)"));
            ItemStack result = safeResult(recipe, access);
            if (result != null && !result.isEmpty()) {
                DataFilter.lostProducers().add(RegistryCutter.itemId(result.getItem()));
            }
            dropped++;
        }
        if (dropped > 0 && core.applies()) {
            recipes.replaceRecipes(kept);
        }
    }

    /** Ingredients made only of cut items or tags the cuts emptied. Custom (non-vanilla) ingredients are not inspected. */
    private static List<Dead> deadIngredients(Recipe<?> recipe) {
        List<Dead> dead = new ArrayList<>();
        List<Ingredient> ingredients;
        try {
            ingredients = recipe.getIngredients();
        } catch (RuntimeException e) {
            return dead;
        }
        for (int i = 0; i < ingredients.size(); i++) {
            Ingredient ingredient = ingredients.get(i);
            if (ingredient.isEmpty() || !ingredient.isVanilla()) {
                continue;
            }
            boolean live = false;
            String cause = null;
            boolean viaTag = false;
            for (Ingredient.Value value : ((IngredientAccessor) (Object) ingredient).scalpel$values()) {
                if (value instanceof Ingredient.ItemValue) {
                    for (ItemStack stack : value.getItems()) {
                        if (RegistryCutter.isCutItem(stack.getItem())) {
                            cause = RegistryCutter.itemId(stack.getItem());
                        } else {
                            live = true;
                        }
                    }
                } else if (value instanceof Ingredient.TagValue tagValue) {
                    String tag = ((TagValueAccessor) tagValue).scalpel$tag().location().toString();
                    if (TagFilter.emptiedItemTags().contains(tag)) {
                        cause = "#" + tag;
                        viaTag = true;
                    } else {
                        live = true;
                    }
                } else {
                    live = true;
                }
            }
            if (!live && cause != null) {
                dead.add(new Dead(i, cause, viaTag));
            }
        }
        return dead;
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
     * Shaped: the dead slots become blank. Shapeless: the dead ingredients are taken out. Returns false (drop the
     * recipe) for other recipe types, or when nothing would be left. Only changes the recipe when apply is set.
     */
    private static boolean rewrite(Recipe<?> recipe, List<Dead> dead, boolean apply) {
        List<Ingredient> ingredients = recipe.getIngredients();
        long left = ingredients.stream().filter(i -> !i.isEmpty()).count() - dead.size();
        if (left <= 0) {
            return false;
        }
        if (recipe instanceof ShapedRecipe) {
            if (apply) {
                for (Dead d : dead) {
                    ingredients.set(d.index(), Ingredient.EMPTY);
                }
            }
            return true;
        }
        if (recipe instanceof ShapelessRecipe) {
            if (apply) {
                for (int i = dead.size() - 1; i >= 0; i--) {
                    ingredients.remove(dead.get(i).index());
                }
            }
            return true;
        }
        return false;
    }

    private static ItemStack safeResult(Recipe<?> recipe, RegistryAccess access) {
        try {
            return recipe.getResultItem(access);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static void onTagsUpdated(TagsUpdatedEvent event) {
        if (event.getUpdateCause() != TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD) {
            return;
        }
        ScalpelCore core = Scalpel.core();
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null && core.settings().cascadeDepth() > 0 && !DataFilter.lostProducers().isEmpty()) {
            core.report().cascade(cascade(core, server), core.settings().cascadeDepth());
        }
        core.report().evaluated(EnumSet.of(ContentType.RECIPE, ContentType.LOOT, ContentType.ADVANCEMENT, ContentType.TAG));
        for (Rule rule : core.report().unmatchedRules(core.rules().rules())) {
            if (rule.types().stream().noneMatch(ContentType::isRegistry)) {
                core.warn("rule matched nothing: " + rule.canonical() + " (" + rule.location() + ")");
            }
        }
        long changes = core.report().changes().size();
        core.log().info("Data filtered: " + changes + " changes" + (core.applies() ? "" : " (dry run, nothing applied)")
                + ". Report: " + core.reportFile());
        core.writeReport();
    }

    private static List<Set<String>> cascade(ScalpelCore core, MinecraftServer server) {
        RegistryAccess access = server.registryAccess();
        List<Cascade.RecipeInfo> infos = new ArrayList<>();
        for (Recipe<?> recipe : server.getRecipeManager().getRecipes()) {
            ItemStack result = safeResult(recipe, access);
            if (result == null || result.isEmpty()) {
                continue;
            }
            List<Set<String>> ingredients = new ArrayList<>();
            try {
                for (Ingredient ingredient : recipe.getIngredients()) {
                    if (ingredient.isEmpty()) {
                        continue;
                    }
                    Set<String> ids = new LinkedHashSet<>();
                    for (ItemStack stack : ingredient.getItems()) {
                        ids.add(RegistryCutter.itemId(stack.getItem()));
                    }
                    ingredients.add(ids);
                }
            } catch (RuntimeException e) {
                ingredients.clear();
            }
            infos.add(new Cascade.RecipeInfo(recipe.getId().toString(), Set.of(RegistryCutter.itemId(result.getItem())), ingredients));
        }
        Set<String> lost = new HashSet<>();
        for (String id : DataFilter.lostProducers()) {
            if (ForgeRegistries.ITEMS.containsKey(new net.minecraft.resources.ResourceLocation(id))) {
                lost.add(id);
            }
        }
        Set<String> cut = new HashSet<>(core.resolver().cutIds(ContentType.ITEM));
        return Cascade.compute(infos, lost, cut, core.settings().cascadeDepth());
    }
}
