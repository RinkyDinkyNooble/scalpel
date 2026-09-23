package com.rinkynooble.scalpel.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Report-only chain analysis: which kept items lost every crafting recipe because of what was cut,
 * then which items depended on those, and so on. Nothing here cuts anything.
 */
public final class Cascade {
    /**
     * @param id          recipe id
     * @param outputs     item ids the recipe produces
     * @param ingredients one entry per ingredient slot, each the set of item ids that fit it (empty = unknown, never blocks)
     */
    public record RecipeInfo(String id, Set<String> outputs, List<Set<String>> ingredients) {
    }

    private Cascade() {
    }

    /**
     * @param current        recipes still loaded after filtering
     * @param lostProducers  items that were the output of at least one dropped recipe
     * @param cut            ids that were cut
     * @param depth          maximum number of levels
     * @return level 1 first; each level is sorted
     */
    public static List<Set<String>> compute(Collection<RecipeInfo> current, Set<String> lostProducers, Set<String> cut, int depth) {
        List<Set<String>> levels = new ArrayList<>();
        if (depth <= 0) {
            return levels;
        }
        Map<String, List<RecipeInfo>> producers = new HashMap<>();
        for (RecipeInfo recipe : current) {
            for (String output : recipe.outputs()) {
                producers.computeIfAbsent(output, k -> new ArrayList<>()).add(recipe);
            }
        }

        Set<String> blocked = new HashSet<>(cut);
        Set<String> first = new TreeSet<>();
        for (String item : lostProducers) {
            if (!cut.contains(item) && !producers.containsKey(item)) {
                first.add(item);
            }
        }
        if (first.isEmpty()) {
            return levels;
        }
        levels.add(first);
        blocked.addAll(first);

        while (levels.size() < depth) {
            Set<String> next = new TreeSet<>();
            for (Map.Entry<String, List<RecipeInfo>> entry : producers.entrySet()) {
                String item = entry.getKey();
                if (blocked.contains(item)) {
                    continue;
                }
                boolean allBlocked = true;
                for (RecipeInfo recipe : entry.getValue()) {
                    if (!isBlocked(recipe, blocked)) {
                        allBlocked = false;
                        break;
                    }
                }
                if (allBlocked) {
                    next.add(item);
                }
            }
            if (next.isEmpty()) {
                break;
            }
            levels.add(next);
            blocked.addAll(next);
        }
        return levels;
    }

    private static boolean isBlocked(RecipeInfo recipe, Set<String> blocked) {
        for (Set<String> alternatives : recipe.ingredients()) {
            if (!alternatives.isEmpty() && blocked.containsAll(alternatives)) {
                return true;
            }
        }
        return false;
    }
}
