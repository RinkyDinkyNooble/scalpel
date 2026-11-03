package com.rinkynooble.scalpel.core.json;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Takes removed enchantments out of a loot table, keeping what it means. An id the registry doesn't have makes the
 * whole file fail to load, and losing a list entry would be wrong here: a tool can't have a removed enchantment, so a
 * condition that asks for one never passes, and a function that gives or reads only removed ones does nothing.
 */
public final class LootEnchantments {
    private static final String RANDOM_CHANCE = "minecraft:random_chance";
    private static final Set<String> COMPOSITE = Set.of("minecraft:inverted", "minecraft:alternative",
            "minecraft:any_of", "minecraft:all_of");

    private LootEnchantments() {
    }

    /** Edits {@code root} in place and returns the removed enchantments it named. */
    public static Set<String> strip(JsonElement root, Predicate<String> isRemoved) {
        Set<String> hits = new LinkedHashSet<>();
        walk(root, isRemoved, hits);
        return hits;
    }

    private static void walk(JsonElement element, Predicate<String> isRemoved, Set<String> hits) {
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            rewriteCondition(object, isRemoved, hits);
            for (Map.Entry<String, JsonElement> child : object.entrySet()) {
                walk(child.getValue(), isRemoved, hits);
            }
        } else if (element.isJsonArray()) {
            Iterator<JsonElement> it = element.getAsJsonArray().iterator();
            while (it.hasNext()) {
                JsonElement child = it.next();
                if (child.isJsonObject() && functionDoesNothing(child.getAsJsonObject(), isRemoved, hits)) {
                    it.remove();
                } else {
                    walk(child, isRemoved, hits);
                }
            }
        }
    }

    /** Prunes a function's list of enchantments. True when nothing is left for it to do. */
    private static boolean functionDoesNothing(JsonObject function, Predicate<String> isRemoved, Set<String> hits) {
        String type = stringId(function, "function");
        if (type == null) {
            return false;
        }
        switch (type) {
            case "minecraft:set_enchantments" -> {
                if (function.get("enchantments") instanceof JsonObject levels && levels.size() > 0) {
                    levels.keySet().removeIf(key -> hit(key, isRemoved, hits));
                    return levels.size() == 0;
                }
            }
            case "minecraft:enchant_randomly" -> {
                // An empty list means every enchantment, so it can't be left empty.
                if (function.get("enchantments") instanceof JsonArray list && !list.isEmpty()) {
                    list.asList().removeIf(entry -> entry.isJsonPrimitive() && hit(entry.getAsString(), isRemoved, hits));
                    return list.isEmpty();
                }
            }
            case "minecraft:apply_bonus" -> {
                String enchantment = stringId(function, "enchantment");
                return enchantment != null && hit(enchantment, isRemoved, hits);
            }
            default -> {
            }
        }
        return false;
    }

    /** A condition that asks for a removed enchantment becomes what it now always gives. */
    private static void rewriteCondition(JsonObject condition, Predicate<String> isRemoved, Set<String> hits) {
        String type = stringId(condition, "condition");
        if (type == null || COMPOSITE.contains(type)) {
            return;
        }
        if (type.equals("minecraft:table_bonus")) {
            String enchantment = stringId(condition, "enchantment");
            if (enchantment != null && hit(enchantment, isRemoved, hits)) {
                JsonElement chances = condition.get("chances");
                boolean any = chances != null && chances.isJsonArray() && !chances.getAsJsonArray().isEmpty();
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

    private static void becomeChance(JsonObject condition, float chance) {
        condition.keySet().clear();
        condition.addProperty("condition", RANDOM_CHANCE);
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
