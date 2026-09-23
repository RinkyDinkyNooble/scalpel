package com.rinkynooble.scalpel.core.json;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Takes cut items out of a recipe file instead of dropping the recipe.
 *
 * <ul>
 *   <li>Shaped crafting: a slot whose ingredient is cut becomes a blank slot.</li>
 *   <li>Shapeless crafting: the cut ingredient is taken out of the list.</li>
 *   <li>Anything else: cut items are taken out of ingredient lists and alternatives. If that leaves a list empty,
 *       or the cut item is not in a list at all (a single input, the result), the recipe is dropped.</li>
 * </ul>
 * A recipe whose result is cut is always dropped.
 */
public final class RecipeRewriter {
    private static final JsonScrub.Policy GENERIC = new JsonScrub.Policy(JsonScrub.EmptyArrays.DROP_WHOLE, Set.of("conditions"));

    public enum Outcome { UNCHANGED, REWRITTEN, DROP }

    private RecipeRewriter() {
    }

    public static Outcome rewrite(JsonObject recipe, Predicate<String> isCut) {
        if (!JsonScrub.references(recipe, isCut)) {
            return Outcome.UNCHANGED;
        }
        for (String resultKey : new String[]{"result", "output"}) {
            JsonElement result = recipe.get(resultKey);
            if (result != null && JsonScrub.references(result, isCut)) {
                return Outcome.DROP;
            }
        }
        String type = recipe.has("type") && recipe.get("type").isJsonPrimitive() ? JsonScrub.asId(recipe.get("type").getAsString()) : null;
        Outcome outcome;
        if ("minecraft:crafting_shaped".equals(type)) {
            outcome = shaped(recipe, isCut);
        } else if ("minecraft:crafting_shapeless".equals(type)) {
            outcome = shapeless(recipe, isCut);
        } else {
            outcome = JsonScrub.scrub(recipe, isCut, GENERIC).dropWhole() ? Outcome.DROP : Outcome.REWRITTEN;
        }
        if (outcome == Outcome.REWRITTEN && JsonScrub.references(recipe, isCut)) {
            return Outcome.DROP;
        }
        return outcome;
    }

    private static Outcome shaped(JsonObject recipe, Predicate<String> isCut) {
        if (!(recipe.get("key") instanceof JsonObject key) || !(recipe.get("pattern") instanceof JsonArray pattern)) {
            return Outcome.DROP;
        }
        List<String> blanked = new ArrayList<>();
        for (String symbol : new ArrayList<>(key.keySet())) {
            JsonElement ingredient = key.get(symbol);
            if (!JsonScrub.references(ingredient, isCut)) {
                continue;
            }
            if (ingredient instanceof JsonArray alternatives && removeReferencing(alternatives, isCut) && !alternatives.isEmpty()) {
                continue;
            }
            key.remove(symbol);
            blanked.add(symbol);
        }
        if (!blanked.isEmpty()) {
            JsonArray newPattern = new JsonArray();
            boolean anyLeft = false;
            for (JsonElement row : pattern) {
                String text = row.getAsString();
                for (String symbol : blanked) {
                    text = text.replace(symbol, " ");
                }
                anyLeft |= !text.isBlank();
                newPattern.add(new JsonPrimitive(text));
            }
            if (!anyLeft) {
                return Outcome.DROP;
            }
            recipe.add("pattern", newPattern);
        }
        return Outcome.REWRITTEN;
    }

    private static Outcome shapeless(JsonObject recipe, Predicate<String> isCut) {
        if (!(recipe.get("ingredients") instanceof JsonArray ingredients)) {
            return Outcome.DROP;
        }
        for (int i = ingredients.size() - 1; i >= 0; i--) {
            JsonElement ingredient = ingredients.get(i);
            if (!JsonScrub.references(ingredient, isCut)) {
                continue;
            }
            if (ingredient instanceof JsonArray alternatives && removeReferencing(alternatives, isCut) && !alternatives.isEmpty()) {
                continue;
            }
            ingredients.remove(i);
        }
        return ingredients.isEmpty() ? Outcome.DROP : Outcome.REWRITTEN;
    }

    /** Removes array elements that reference a cut id. Returns true if anything was removed. */
    private static boolean removeReferencing(JsonArray array, Predicate<String> isCut) {
        boolean removed = false;
        for (int i = array.size() - 1; i >= 0; i--) {
            if (JsonScrub.references(array.get(i), isCut)) {
                array.remove(i);
                removed = true;
            }
        }
        return removed;
    }
}
