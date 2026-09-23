package com.rinkynooble.scalpel.core;

import java.util.Locale;

/** What happens to a recipe that uses cut content as an ingredient. */
public enum RecipeMode {
    /** Drop the whole recipe. */
    DROP,
    /** Take the cut item out of the recipe and keep the rest, when the recipe still makes sense afterwards. */
    REWRITE;

    public String keyword() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static RecipeMode parse(String keyword) {
        for (RecipeMode mode : values()) {
            if (mode.keyword().equals(keyword)) {
                return mode;
            }
        }
        return null;
    }
}
