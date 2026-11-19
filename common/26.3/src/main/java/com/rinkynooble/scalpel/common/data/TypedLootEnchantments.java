package com.rinkynooble.scalpel.common.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.rinkynooble.scalpel.core.json.JsonScrub;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Takes removed enchantments out of a loot table in the 26.3 format, keeping what it means (core's
 * {@code LootEnchantments} does the same for the older format). 26.3 keys every function and condition by
 * {@code type}, gives entries, pools and tables one {@code condition} (an object or a predicate id) and one
 * {@code modifier} (a function, an inline list of functions, or an item modifier id), and composite conditions hold
 * {@code terms} / {@code term}. A loot table naming a removed enchantment would fail to load and stop the data load.
 * <ul>
 *   <li>A condition that asks for a removed enchantment never passes any more, so it becomes {@code random_chance} 0
 *   (a {@code table_bonus} becomes its level-0 chance).</li>
 *   <li>A function that gives or reads only removed enchantments does nothing, so it goes.</li>
 * </ul>
 */
final class TypedLootEnchantments {
    private static final String RANDOM_CHANCE = "minecraft:random_chance";
    private static final Set<String> COMPOSITE = Set.of("minecraft:inverted", "minecraft:any_of", "minecraft:all_of");

    private TypedLootEnchantments() {
    }

    /** Edits {@code root} in place and returns the removed enchantments it named. */
    static Set<String> strip(JsonElement root, Predicate<String> isRemoved) {
        Set<String> hits = new LinkedHashSet<>();
        walk(root, isRemoved, hits);
        return hits;
    }

    private static void walk(JsonElement element, Predicate<String> isRemoved, Set<String> hits) {
        if (element instanceof JsonArray array) {
            for (JsonElement child : array) {
                walk(child, isRemoved, hits);
            }
            return;
        }
        if (!(element instanceof JsonObject object)) {
            return;
        }
        List<String> keys = new ArrayList<>(object.keySet());
        for (String key : keys) {
            JsonElement value = object.get(key);
            switch (key) {
                case "condition", "term" -> condition(value, isRemoved, hits);
                case "terms" -> {
                    if (value instanceof JsonArray terms) {
                        for (JsonElement term : terms) {
                            condition(term, isRemoved, hits);
                        }
                    } else {
                        condition(value, isRemoved, hits);
                    }
                }
                // A table's, pool's or entry's function, and a sequence's list.
                case "modifier", "functions" -> functions(object, key, isRemoved, hits);
                default -> walk(value, isRemoved, hits);
            }
        }
    }

    /** One condition slot: an inline condition is rewritten; a predicate id is left to its own registry. */
    private static void condition(JsonElement element, Predicate<String> isRemoved, Set<String> hits) {
        if (!(element instanceof JsonObject condition)) {
            return;
        }
        String type = stringId(condition, "type");
        if (type == null) {
            return;
        }
        if (COMPOSITE.contains(type)) {
            walk(condition, isRemoved, hits);
            return;
        }
        if (type.equals("minecraft:table_bonus")) {
            String enchantment = stringId(condition, "enchantment");
            if (enchantment != null && hit(enchantment, isRemoved, hits)) {
                JsonElement chances = condition.get("chances");
                boolean any = chances instanceof JsonArray list && !list.isEmpty();
                becomeChance(condition, any ? chances.getAsJsonArray().get(0).getAsFloat() : 0F);
            }
            return;
        }
        Set<String> named = JsonScrub.findReferences(condition, isRemoved);
        if (!named.isEmpty()) {
            hits.addAll(named);
            becomeChance(condition, 0F);
        }
    }

    /** {@code owner.key} holds one function or a list of them; functions left with nothing to do go. */
    private static void functions(JsonObject owner, String key, Predicate<String> isRemoved, Set<String> hits) {
        JsonElement value = owner.get(key);
        if (value instanceof JsonObject function) {
            if (doesNothing(function, isRemoved, hits)) {
                owner.remove(key);
            } else {
                walk(function, isRemoved, hits);
            }
        } else if (value instanceof JsonArray list) {
            Iterator<JsonElement> it = list.iterator();
            while (it.hasNext()) {
                JsonElement child = it.next();
                if (child instanceof JsonObject function && doesNothing(function, isRemoved, hits)) {
                    it.remove();
                } else {
                    walk(child, isRemoved, hits);
                }
            }
            if (list.isEmpty() && key.equals("modifier")) {
                owner.remove(key);
            }
        }
    }

    /** Prunes an enchanting function's enchantments. True when nothing is left for it to do. */
    private static boolean doesNothing(JsonObject function, Predicate<String> isRemoved, Set<String> hits) {
        String type = stringId(function, "type");
        if (type == null) {
            return false;
        }
        switch (type) {
            case "minecraft:set_enchantments" -> {
                if (function.get("enchantments") instanceof JsonObject levels && levels.size() > 0) {
                    levels.keySet().removeIf(id -> hit(id, isRemoved, hits));
                    return levels.size() == 0;
                }
            }
            case "minecraft:enchant_randomly", "minecraft:enchant_with_levels" -> {
                return optionsGone(function, isRemoved, hits);
            }
            case "minecraft:apply_bonus", "minecraft:enchanted_count_increase" -> {
                String enchantment = stringId(function, "enchantment");
                return enchantment != null && hit(enchantment, isRemoved, hits);
            }
            default -> {
            }
        }
        return false;
    }

    /** {@code options}: one id, a tag (left to the tag filter), or a list. A removed id or an emptied list leaves nothing. */
    private static boolean optionsGone(JsonObject function, Predicate<String> isRemoved, Set<String> hits) {
        JsonElement options = function.get("options");
        if (options instanceof JsonArray list && !list.isEmpty()) {
            list.asList().removeIf(entry -> entry.isJsonPrimitive() && hit(entry.getAsString(), isRemoved, hits));
            return list.isEmpty();
        }
        if (options != null && options.isJsonPrimitive() && !options.getAsString().startsWith("#")) {
            return hit(options.getAsString(), isRemoved, hits);
        }
        return false;
    }

    private static void becomeChance(JsonObject condition, float chance) {
        condition.keySet().clear();
        condition.addProperty("type", RANDOM_CHANCE);
        condition.addProperty("chance", chance);
    }

    private static boolean hit(String value, Predicate<String> isRemoved, Set<String> hits) {
        String id = JsonScrub.asId(value);
        if (id != null && isRemoved.test(id)) {
            hits.add(id);
            return true;
        }
        return false;
    }

    private static String stringId(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                ? JsonScrub.asId(value.getAsString()) : null;
    }
}
