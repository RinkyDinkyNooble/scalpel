package com.rinkynooble.scalpel.core.json;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Reads the ingredients of a recipe file of a type Scalpel has no model for (a mod's own recipe type), the way
 * vanilla writes an ingredient value: {@code {"item": id}} or {@code {"tag": id}}. Mods nest those values inside their
 * own keys: TaCZ's gun smith table writes {@code materials[].item: {"tag": ...}}, Mekanism
 * {@code input.ingredient: {"tag": ...}}.
 *
 * <p>An array of values under a key that names one ingredient ({@code "ingredient"}, a shaped recipe's key letter) is
 * one ingredient with alternatives. Under a plural key ({@code "ingredients"}, {@code "materials"}) each value is an
 * ingredient of its own.
 */
public final class RecipeIngredients {
    /** Forge's load conditions name tags too, but they are not ingredients. */
    private static final Set<String> SKIP = Set.of("conditions");
    /** A value with one of these keys is a fluid or chemical amount (Mekanism's {"tag": ..., "amount": n}). */
    private static final Set<String> NOT_ITEMS = Set.of("amount", "fluid", "gas", "infuse_type", "pigment", "slurry", "chemical");

    private RecipeIngredients() {
    }

    /**
     * The emptied tags behind ingredients that can no longer be filled: every alternative is a cut item or an emptied
     * tag, and at least one is a tag. (An ingredient made only of cut items names them directly, which the file filter
     * already handles.) Empty when no ingredient is dead.
     *
     * @param isCutItem    takes an item id
     * @param isEmptiedTag takes a tag id without {@code #}
     */
    public static Set<String> deadTags(JsonElement recipe, Predicate<String> isCutItem, Predicate<String> isEmptiedTag) {
        Set<String> dead = new LinkedHashSet<>();
        walk(recipe, null, isCutItem, isEmptiedTag, dead);
        return dead;
    }

    private static void walk(JsonElement element, String key, Predicate<String> isCutItem, Predicate<String> isEmptiedTag, Set<String> dead) {
        if (key != null && SKIP.contains(key)) {
            return;
        }
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            Value value = value(object, isCutItem, isEmptiedTag);
            if (value != null) {
                if (value.dead() && value.tag() != null) {
                    dead.add(value.tag());
                }
                return;
            }
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                walk(entry.getValue(), entry.getKey(), isCutItem, isEmptiedTag, dead);
            }
        } else if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            boolean oneIngredient = key == null || !key.endsWith("s");
            if (oneIngredient && !array.isEmpty() && allValues(array)) {
                Set<String> tags = new LinkedHashSet<>();
                for (JsonElement child : array) {
                    Value value = value(child.getAsJsonObject(), isCutItem, isEmptiedTag);
                    if (!value.dead()) {
                        return;
                    }
                    if (value.tag() != null) {
                        tags.add(value.tag());
                    }
                }
                dead.addAll(tags);
                return;
            }
            for (JsonElement child : array) {
                walk(child, null, isCutItem, isEmptiedTag, dead);
            }
        }
    }

    /** One ingredient value: dead when its item is cut or its tag is emptied. Tag is set for tag values. */
    private record Value(boolean dead, String tag) {
    }

    private static Value value(JsonObject object, Predicate<String> isCutItem, Predicate<String> isEmptiedTag) {
        for (String key : NOT_ITEMS) {
            if (object.has(key)) {
                return null;
            }
        }
        String tag = string(object, "tag");
        if (tag != null) {
            String id = tagId(tag);
            return new Value(isEmptiedTag.test(id), id);
        }
        String item = string(object, "item");
        if (item != null) {
            String id = JsonScrub.asId(item);
            return new Value(id != null && isCutItem.test(id), null);
        }
        return null;
    }

    private static boolean allValues(JsonArray array) {
        for (JsonElement child : array) {
            if (!child.isJsonObject() || (string(child.getAsJsonObject(), "item") == null && string(child.getAsJsonObject(), "tag") == null)) {
                return false;
            }
        }
        return true;
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : null;
    }

    private static String tagId(String tag) {
        String id = tag.startsWith("#") ? tag.substring(1) : tag;
        return id.indexOf(':') < 0 ? "minecraft:" + id : id;
    }
}
