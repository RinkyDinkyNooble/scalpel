package com.rinkynooble.scalpel.core.rules;

import com.rinkynooble.scalpel.core.ContentType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * All loaded rules, indexed per content type so a lookup only tests the rules that could match:
 * exact ids by hash lookup, patterns with a fixed namespace by namespace, and the rest in one list.
 */
public final class RuleSet {
    private final List<Rule> rules;
    private final Map<ContentType, Index> indexes = new EnumMap<>(ContentType.class);

    public RuleSet(List<Rule> rules) {
        this.rules = List.copyOf(rules);
        for (ContentType type : ContentType.values()) {
            indexes.put(type, new Index());
        }
        for (Rule rule : this.rules) {
            for (ContentType type : rule.types()) {
                indexes.get(type).add(rule);
            }
        }
    }

    public static RuleSet empty() {
        return new RuleSet(List.of());
    }

    public List<Rule> rules() {
        return rules;
    }

    public boolean isEmpty() {
        return rules.isEmpty();
    }

    public boolean hasRulesFor(ContentType type) {
        return !indexes.get(type).isEmpty();
    }

    /** Every rule matching {@code id} for {@code type}, in file order. Records the match when {@code record} is set. */
    public List<Rule> matching(ContentType type, String id, boolean record) {
        List<Rule> found = indexes.get(type).matching(id);
        if (record) {
            for (Rule rule : found) {
                rule.recordMatch(type, id);
            }
        }
        return found;
    }

    private static final class Index {
        private final Map<String, List<Rule>> exact = new HashMap<>();
        private final Map<String, List<Rule>> byNamespace = new HashMap<>();
        private final List<Rule> other = new ArrayList<>();
        private int size;

        void add(Rule rule) {
            size++;
            IdPattern pattern = rule.pattern();
            if (pattern instanceof IdPattern.Exact exactPattern) {
                exact.computeIfAbsent(exactPattern.id(), k -> new ArrayList<>(1)).add(rule);
            } else if (pattern.namespaceHint() != null) {
                byNamespace.computeIfAbsent(pattern.namespaceHint(), k -> new ArrayList<>()).add(rule);
            } else {
                other.add(rule);
            }
        }

        boolean isEmpty() {
            return size == 0;
        }

        List<Rule> matching(String id) {
            if (size == 0) {
                return Collections.emptyList();
            }
            List<Rule> found = null;
            List<Rule> exactRules = exact.get(id);
            if (exactRules != null) {
                found = new ArrayList<>(exactRules);
            }
            int colon = id.indexOf(':');
            if (colon > 0) {
                List<Rule> nsRules = byNamespace.get(id.substring(0, colon));
                if (nsRules != null) {
                    found = addMatches(found, nsRules, id);
                }
            }
            if (!other.isEmpty()) {
                found = addMatches(found, other, id);
            }
            if (found == null) {
                return Collections.emptyList();
            }
            if (found.size() > 1) {
                found.sort((a, b) -> a.file().equals(b.file()) ? Integer.compare(a.line(), b.line()) : a.file().compareTo(b.file()));
            }
            return found;
        }

        private static List<Rule> addMatches(List<Rule> found, List<Rule> candidates, String id) {
            for (Rule rule : candidates) {
                if (rule.pattern().matches(id)) {
                    if (found == null) {
                        found = new ArrayList<>(2);
                    }
                    found.add(rule);
                }
            }
            return found;
        }
    }
}
