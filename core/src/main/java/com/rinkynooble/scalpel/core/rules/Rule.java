package com.rinkynooble.scalpel.core.rules;

import com.rinkynooble.scalpel.core.ContentType;
import com.rinkynooble.scalpel.core.RecipeMode;
import com.rinkynooble.scalpel.core.Verb;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** One parsed line of a rules file. Tracks which ids it matched, per type, for the report and unmatched warnings. */
public final class Rule {
    private final Verb verb;
    private final String typeKeyword;
    private final Set<ContentType> types;
    private final IdPattern pattern;
    private final RecipeMode recipeMode;
    private final String file;
    private final int line;
    private final Map<ContentType, Set<String>> matched = new EnumMap<>(ContentType.class);

    public Rule(Verb verb, String typeKeyword, Set<ContentType> types, IdPattern pattern, RecipeMode recipeMode, String file, int line) {
        this.verb = verb;
        this.typeKeyword = typeKeyword;
        this.types = Collections.unmodifiableSet(EnumSet.copyOf(types));
        this.pattern = pattern;
        this.recipeMode = recipeMode;
        this.file = file;
        this.line = line;
        for (ContentType type : types) {
            matched.put(type, ConcurrentHashMap.newKeySet());
        }
    }

    public Verb verb() {
        return verb;
    }

    public Set<ContentType> types() {
        return types;
    }

    public IdPattern pattern() {
        return pattern;
    }

    /** Per-rule override of the recipe setting, or null to use the global one. */
    public RecipeMode recipeMode() {
        return recipeMode;
    }

    public String file() {
        return file;
    }

    public int line() {
        return line;
    }

    /** {@code decor.rules:12} */
    public String location() {
        return file + ":" + line;
    }

    /** The rule in canonical form, used for hashing and display. Comments and spacing are gone. */
    public String canonical() {
        StringBuilder sb = new StringBuilder();
        sb.append(verb.keyword()).append(' ').append(typeKeyword).append(' ').append(pattern.source());
        if (recipeMode != null) {
            sb.append(' ').append(recipeMode.keyword());
        }
        return sb.toString();
    }

    void recordMatch(ContentType type, String id) {
        Set<String> set = matched.get(type);
        if (set != null) {
            set.add(id);
        }
    }

    public int matchCount(ContentType type) {
        Set<String> set = matched.get(type);
        return set == null ? 0 : set.size();
    }

    public int matchCount() {
        int total = 0;
        for (Set<String> set : matched.values()) {
            total += set.size();
        }
        return total;
    }

    /** Forget matches for the given types, used before data is reloaded. */
    public void clearMatches(Set<ContentType> of) {
        for (ContentType type : of) {
            Set<String> set = matched.get(type);
            if (set != null) {
                set.clear();
            }
        }
    }

    @Override
    public String toString() {
        return canonical() + " (" + location() + ")";
    }
}
