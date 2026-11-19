package com.rinkynooble.scalpel.common.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.rinkynooble.scalpel.core.json.JsonScrub;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Where a loot table names another loot table (26.3, where loot tables are a datapack registry and these names are
 * registry entries): a {@code loot_table} entry's {@code value}, and a {@code set_loot_table} function's {@code name}
 * (26.3 keys entries and functions alike by {@code type}).
 * When the named table was left out, the entry or function goes (a pool without it still works; an item without the
 * function is a plain item).
 */
final class LootReferences {
    private LootReferences() {
    }

    /** Strips entries and functions naming a left-out table, in place. Returns how many went; {@code hits} gets their names. */
    static int strip(JsonElement json, Set<String> gone, Set<String> hits) {
        int removed = 0;
        if (json instanceof JsonArray array) {
            Iterator<JsonElement> it = array.iterator();
            while (it.hasNext()) {
                JsonElement element = it.next();
                String hit = namesGone(element, gone);
                if (hit != null) {
                    hits.add(hit);
                    it.remove();
                    removed++;
                } else {
                    removed += strip(element, gone, hits);
                }
            }
        } else if (json instanceof JsonObject object) {
            Iterator<Map.Entry<String, JsonElement>> it = object.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, JsonElement> entry = it.next();
                // 26.3: an entry's, pool's or table's one function sits under "modifier" on its own.
                String hit = entry.getKey().equals("modifier") ? namesGone(entry.getValue(), gone) : null;
                if (hit != null) {
                    hits.add(hit);
                    it.remove();
                    removed++;
                } else {
                    removed += strip(entry.getValue(), gone, hits);
                }
            }
        }
        return removed;
    }

    /** The left-out table this entry or function names, when it names only left-out ones; else null. */
    private static String namesGone(JsonElement element, Set<String> gone) {
        if (!(element instanceof JsonObject object)) {
            return null;
        }
        String type = id(object.get("type"));
        if ("minecraft:loot_table".equals(type)) {
            return onlyGone(object, "value", gone);
        }
        if ("minecraft:set_loot_table".equals(type)) {
            return onlyGone(object, "name", gone);
        }
        return null;
    }

    /**
     * For {@code object.key} as one id or a list of ids: takes left-out ids out of a list, and returns a left-out id
     * when nothing else is named (the whole entry must go). Inline tables and tags stay.
     */
    private static String onlyGone(JsonObject object, String key, Set<String> gone) {
        JsonElement value = object.get(key);
        if (value == null) {
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
            JsonElement entry = it.next();
            if (entry.isJsonPrimitive()) {
                String id = JsonScrub.asId(entry.getAsString());
                if (id != null && gone.contains(id)) {
                    last = id;
                    it.remove();
                }
            }
        }
        return last != null && list.isEmpty() ? last : null;
    }

    private static String id(JsonElement element) {
        return element != null && element.isJsonPrimitive() ? JsonScrub.asId(element.getAsString()) : null;
    }
}
