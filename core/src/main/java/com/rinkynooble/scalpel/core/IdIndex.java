package com.rinkynooble.scalpel.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Sorted id arrays for fast command suggestions. Built once; a lookup is two binary searches and a short scan,
 * so typing stays quick with tens of thousands of ids. Matches the full id ({@code examplemod:ru...})
 * or just the path ({@code ru...}).
 */
public final class IdIndex {
    private static final char SEPARATOR = '\u0000';

    private final String[] ids;
    private final String[] pathKeys;

    public IdIndex(Collection<String> ids) {
        this.ids = ids.stream().distinct().sorted().toArray(String[]::new);
        this.pathKeys = new String[this.ids.length];
        for (int i = 0; i < this.ids.length; i++) {
            String id = this.ids[i];
            this.pathKeys[i] = id.substring(id.indexOf(':') + 1) + SEPARATOR + id;
        }
        Arrays.sort(this.pathKeys);
    }

    public int size() {
        return ids.length;
    }

    public List<String> all() {
        return Arrays.asList(ids);
    }

    public List<String> suggest(String input, int limit) {
        Set<String> result = new LinkedHashSet<>();
        scanPrefix(ids, input, limit, result, false);
        if (input.indexOf(':') < 0 && result.size() < limit) {
            scanPrefix(pathKeys, input, limit, result, true);
        }
        return new ArrayList<>(result);
    }

    private static void scanPrefix(String[] sorted, String prefix, int limit, Set<String> out, boolean pathKeys) {
        int start = Arrays.binarySearch(sorted, prefix);
        if (start < 0) {
            start = -start - 1;
        }
        for (int i = start; i < sorted.length && out.size() < limit; i++) {
            String entry = sorted[i];
            if (!entry.startsWith(prefix)) {
                break;
            }
            out.add(pathKeys ? entry.substring(entry.indexOf(SEPARATOR) + 1) : entry);
        }
    }
}
