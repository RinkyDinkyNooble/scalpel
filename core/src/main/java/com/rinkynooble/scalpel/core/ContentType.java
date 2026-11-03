package com.rinkynooble.scalpel.core;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/** What a rule applies to. Registry types are cut at registration; data types are filtered when data loads. */
public enum ContentType {
    ITEM(true),
    BLOCK(true),
    ENTITY(true),
    ENCHANTMENT(true),
    RECIPE(false),
    LOOT(false),
    ADVANCEMENT(false),
    TAG(false);

    public static final Set<ContentType> REGISTRY_TYPES = EnumSet.of(ITEM, BLOCK, ENTITY, ENCHANTMENT);
    /** What {@code any} means in a rule. Enchantments are left out: they always take their own rule. */
    public static final Set<ContentType> ANY_TYPES = EnumSet.of(ITEM, BLOCK, ENTITY);

    private final boolean registry;

    ContentType(boolean registry) {
        this.registry = registry;
    }

    public boolean isRegistry() {
        return registry;
    }

    public String keyword() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Parses a rule-file type keyword. {@code any} expands to {@link #ANY_TYPES}. Returns null if unknown. */
    public static Set<ContentType> parse(String keyword) {
        if (keyword.equals("any")) {
            return EnumSet.copyOf(ANY_TYPES);
        }
        for (ContentType type : values()) {
            if (type.keyword().equals(keyword)) {
                return EnumSet.of(type);
            }
        }
        return null;
    }
}
