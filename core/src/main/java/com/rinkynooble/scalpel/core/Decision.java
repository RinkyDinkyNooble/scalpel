package com.rinkynooble.scalpel.core;

import com.rinkynooble.scalpel.core.rules.Rule;

import java.util.List;

/**
 * The outcome for one id.
 *
 * @param action  what happens to it
 * @param rule    the rule that decided it, or null when no rule matched
 * @param matched every rule that matched (for {@code /scalpel explain})
 * @param note    why the result differs from the rule's verb, or how it was linked; null when there is nothing to add
 */
public record Decision(Action action, Rule rule, List<Rule> matched, String note) {
    public enum Action {
        /** No rule applies. */
        NONE,
        /** A keep rule applies. */
        KEEP,
        /** A cut rule matched, but the id is protected. */
        PROTECTED,
        /** Registered as a Redacted placeholder. */
        REDACT,
        /** Never registered (registry types) or dropped (data types). */
        REMOVE
    }

    public static final Decision NONE = new Decision(Action.NONE, null, List.of(), null);

    public boolean isCut() {
        return action == Action.REDACT || action == Action.REMOVE;
    }

    /** The rule's recipe option, falling back to the global setting. */
    public RecipeMode recipeMode(Settings settings) {
        if (rule != null && rule.recipeMode() != null) {
            return rule.recipeMode();
        }
        return settings.ingredientMode();
    }

    /** Short human form: {@code redact (decor.rules:4)}, {@code remove (linked: block examplemod:ruby_block)}. */
    public String describe() {
        StringBuilder sb = new StringBuilder(action.name().toLowerCase(java.util.Locale.ROOT));
        if (rule != null) {
            sb.append(" (").append(rule.location()).append(')');
        }
        if (note != null) {
            sb.append(" [").append(note).append(']');
        }
        return sb.toString();
    }
}
