package com.rinkynooble.scalpel.common.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.rinkynooble.scalpel.core.json.JsonScrub;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * What an advancement names in the recipe and loot table registries (26.3, where both are datapack registries loaded
 * with the advancements): criteria {@code recipe_unlocked} and {@code recipe_crafted} name recipes, criteria
 * {@code player_generates_container_loot} and the {@code loot} reward name loot tables. A name of a left-out entry would
 * stop the whole data load, so it comes out here.
 */
final class AdvancementReferences {
    private static final Map<String, String> RECIPE_CRITERIA = Map.of(
            "minecraft:recipe_unlocked", "recipes",
            "minecraft:recipe_crafted", "recipes");
    private static final Map<String, String> LOOT_CRITERIA = Map.of(
            "minecraft:player_generates_container_loot", "loot_tables");

    private AdvancementReferences() {
    }

    /**
     * Takes left-out recipes and loot tables out of the advancement's criteria and rewards, in place. Returns what the
     * advancement can't do without ("removed recipe minecraft:x") when a criterion is left with nothing to name, so the
     * advancement can never be earned and must go; null when it can stay.
     */
    static String fix(JsonElement advancement, Set<String> goneRecipes, Set<String> goneLoot) {
        if (!advancement.isJsonObject()) {
            return null;
        }
        JsonObject root = advancement.getAsJsonObject();
        if (root.get("criteria") instanceof JsonObject criteria) {
            for (Map.Entry<String, JsonElement> criterion : criteria.entrySet()) {
                if (!(criterion.getValue() instanceof JsonObject body) || !(body.get("conditions") instanceof JsonObject conditions)
                        || !body.has("trigger") || !body.get("trigger").isJsonPrimitive()) {
                    continue;
                }
                String trigger = JsonScrub.asId(body.get("trigger").getAsString());
                String missing = null;
                if (RECIPE_CRITERIA.containsKey(trigger)) {
                    missing = strip(conditions, RECIPE_CRITERIA.get(trigger), goneRecipes);
                    missing = missing == null ? null : "removed recipe " + missing;
                } else if (LOOT_CRITERIA.containsKey(trigger)) {
                    missing = strip(conditions, LOOT_CRITERIA.get(trigger), goneLoot);
                    missing = missing == null ? null : "removed loot table " + missing;
                }
                if (missing != null) {
                    return missing;
                }
            }
        }
        if (root.get("rewards") instanceof JsonObject rewards) {
            // A reward is optional: the advancement stays, without the left-out ones.
            if (strip(rewards, "loot", goneLoot) != null) {
                rewards.remove("loot");
            }
            if (strip(rewards, "recipes", goneRecipes) != null) {
                rewards.remove("recipes");
            }
        }
        return null;
    }

    /**
     * Takes left-out ids out of {@code holder.key} (one id or a list of ids). Returns the left-out id when nothing is
     * left (the field named only left-out entries), else null. Tags ({@code #...}) and inline entries stay.
     */
    private static String strip(JsonObject holder, String key, Set<String> gone) {
        JsonElement value = holder.get(key);
        if (value == null || gone.isEmpty()) {
            return null;
        }
        if (value.isJsonPrimitive()) {
            String id = JsonScrub.asId(value.getAsString());
            return id != null && gone.contains(id) ? id : null;
        }
        if (!(value instanceof JsonArray list)) {
            return null;
        }
        String last = null;
        Iterator<JsonElement> it = list.iterator();
        while (it.hasNext()) {
            JsonElement element = it.next();
            if (element.isJsonPrimitive()) {
                String id = JsonScrub.asId(element.getAsString());
                if (id != null && gone.contains(id)) {
                    last = id;
                    it.remove();
                }
            }
        }
        return last != null && list.isEmpty() ? last : null;
    }
}
