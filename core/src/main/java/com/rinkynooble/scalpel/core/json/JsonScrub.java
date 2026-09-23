package com.rinkynooble.scalpel.core.json;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Finds and removes references to cut ids inside data JSON (recipes, loot tables, worldgen, ...).
 *
 * <p>A reference is any string value (or object key) equal to a cut id. Bare names such as {@code "stone"}
 * are read as {@code minecraft:stone}, the same way the game reads them. Strings starting with {@code #} are
 * tag references and are ignored.
 *
 * <p>Removal takes out the smallest array element that contains the reference: one entry of a loot pool,
 * one ore target, one alternative of an ingredient. Arrays under names in {@link Policy#escalatePast()}
 * (for example {@code conditions}) are skipped so a condition is never silently deleted; the element
 * that owns the condition goes instead. When no array holds the reference the whole file has to go,
 * and the caller decides what that means.
 */
public final class JsonScrub {
    private JsonScrub() {
    }

    public enum EmptyArrays {
        /** An array left empty stays empty. */
        KEEP,
        /** An array left empty is itself removed from the next array up. */
        ESCALATE,
        /** An array left empty means the whole file is dropped. */
        DROP_WHOLE
    }

    public record Policy(EmptyArrays emptyArrays, Set<String> escalatePast) {
    }

    /**
     * @param dropWhole true when the file cannot be fixed by removing parts of it
     * @param removed   how many elements or keys were removed
     * @param hits      the cut ids that were found
     */
    public record Result(boolean dropWhole, int removed, Set<String> hits) {
        public boolean changed() {
            return dropWhole || removed > 0;
        }
    }

    /** Resolves a JSON string the way the game would read it as an id, or returns null if it cannot be one. */
    public static String asId(String value) {
        if (value.isEmpty() || value.charAt(0) == '#' || value.length() > 256) {
            return null;
        }
        int colon = value.indexOf(':');
        if (colon < 0) {
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                if (!((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '/' || c == '.' || c == '-')) {
                    return null;
                }
            }
            return "minecraft:" + value;
        }
        return value;
    }

    /** Every cut id referenced anywhere in {@code root}. */
    public static Set<String> findReferences(JsonElement root, Predicate<String> isCut) {
        Set<String> found = new LinkedHashSet<>();
        collect(root, isCut, found);
        return found;
    }

    public static boolean references(JsonElement root, Predicate<String> isCut) {
        return findFirst(root, isCut, new ArrayList<>()) != null;
    }

    private static void collect(JsonElement element, Predicate<String> isCut, Set<String> found) {
        if (element.isJsonPrimitive()) {
            String id = idIfCut(element.getAsJsonPrimitive(), isCut);
            if (id != null) {
                found.add(id);
            }
        } else if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                collect(child, isCut, found);
            }
        } else if (element.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                String keyId = asId(entry.getKey());
                if (keyId != null && entry.getKey().indexOf(':') > 0 && isCut.test(keyId)) {
                    found.add(keyId);
                }
                collect(entry.getValue(), isCut, found);
            }
        }
    }

    private static String idIfCut(JsonPrimitive primitive, Predicate<String> isCut) {
        if (!primitive.isString()) {
            return null;
        }
        String id = asId(primitive.getAsString());
        return id != null && isCut.test(id) ? id : null;
    }

    /** One step from a container into a child: an object key or an array index. */
    private record Step(JsonElement container, String key, int index) {
    }

    /** A found reference: the path to it, and whether the reference is an object key rather than a value. */
    private record Found(List<Step> path, String id, boolean isKey) {
    }

    private static Found findFirst(JsonElement element, Predicate<String> isCut, List<Step> path) {
        if (element.isJsonPrimitive()) {
            String id = idIfCut(element.getAsJsonPrimitive(), isCut);
            return id == null ? null : new Found(new ArrayList<>(path), id, false);
        }
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (int i = 0; i < array.size(); i++) {
                path.add(new Step(array, null, i));
                Found found = findFirst(array.get(i), isCut, path);
                path.remove(path.size() - 1);
                if (found != null) {
                    return found;
                }
            }
            return null;
        }
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                String key = entry.getKey();
                if (key.indexOf(':') > 0) {
                    String keyId = asId(key);
                    if (keyId != null && isCut.test(keyId)) {
                        List<Step> keyPath = new ArrayList<>(path);
                        keyPath.add(new Step(object, key, -1));
                        return new Found(keyPath, keyId, true);
                    }
                }
                path.add(new Step(object, key, -1));
                Found found = findFirst(entry.getValue(), isCut, path);
                path.remove(path.size() - 1);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /** Removes every reference according to {@code policy}. Mutates {@code root}. */
    public static Result scrub(JsonElement root, Predicate<String> isCut, Policy policy) {
        Set<String> hits = new LinkedHashSet<>();
        int removed = 0;
        while (true) {
            Found found = findFirst(root, isCut, new ArrayList<>());
            if (found == null) {
                return new Result(false, removed, hits);
            }
            hits.add(found.id());
            if (found.isKey()) {
                Step step = found.path().get(found.path().size() - 1);
                step.container().getAsJsonObject().remove(step.key());
                removed++;
                continue;
            }
            int level = found.path().size() - 1;
            while (true) {
                level = innermostArray(found.path(), level, policy);
                if (level < 0) {
                    return new Result(true, removed, hits);
                }
                Step step = found.path().get(level);
                JsonArray array = step.container().getAsJsonArray();
                array.remove(step.index());
                removed++;
                if (!array.isEmpty() || policy.emptyArrays() == EmptyArrays.KEEP) {
                    break;
                }
                if (policy.emptyArrays() == EmptyArrays.DROP_WHOLE) {
                    return new Result(true, removed, hits);
                }
                level--;
            }
        }
    }

    /** Index of the deepest step at or above {@code from} that removes an element from an eligible array, or -1. */
    private static int innermostArray(List<Step> path, int from, Policy policy) {
        for (int i = from; i >= 0; i--) {
            Step step = path.get(i);
            if (!step.container().isJsonArray()) {
                continue;
            }
            String arrayName = i > 0 ? path.get(i - 1).key() : null;
            if (arrayName != null && policy.escalatePast().contains(arrayName)) {
                continue;
            }
            return i;
        }
        return -1;
    }
}
