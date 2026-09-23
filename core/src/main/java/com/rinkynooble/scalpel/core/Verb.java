package com.rinkynooble.scalpel.core;

import java.util.Locale;

/** Rule verbs, in precedence order: when several rules match one id, the lowest ordinal wins. */
public enum Verb {
    KEEP,
    REDACT,
    REMOVE;

    public String keyword() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Verb parse(String keyword) {
        for (Verb verb : values()) {
            if (verb.keyword().equals(keyword)) {
                return verb;
            }
        }
        return null;
    }
}
