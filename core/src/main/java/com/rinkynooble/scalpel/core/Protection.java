package com.rinkynooble.scalpel.core;

import java.util.Set;

/**
 * Ids that must not be cut. The hard set breaks the registries themselves and can never be cut.
 * The soft set is resolved internally by the game (world generation defaults, fluids, core entities)
 * and can be unlocked with {@code protectCriticalIds = false}.
 */
public final class Protection {
    private static final Set<String> HARD_BLOCKS = Set.of(
            "minecraft:air", "minecraft:void_air", "minecraft:cave_air");
    private static final Set<String> HARD_ITEMS = Set.of(
            "minecraft:air");
    private static final Set<String> HARD_ENTITIES = Set.of(
            "minecraft:pig", "minecraft:player");

    private static final Set<String> SOFT_BLOCKS = Set.of(
            "minecraft:stone", "minecraft:deepslate", "minecraft:netherrack", "minecraft:end_stone",
            "minecraft:bedrock", "minecraft:water", "minecraft:lava", "minecraft:fire", "minecraft:soul_fire",
            "minecraft:nether_portal", "minecraft:end_portal", "minecraft:end_gateway",
            "minecraft:moving_piston", "minecraft:piston_head", "minecraft:bubble_column",
            "minecraft:structure_void", "minecraft:structure_block", "minecraft:jigsaw",
            "minecraft:barrier", "minecraft:light");
    private static final Set<String> SOFT_ITEMS = Set.of(
            "minecraft:barrier", "minecraft:light", "minecraft:structure_void", "minecraft:structure_block",
            "minecraft:jigsaw", "minecraft:debug_stick", "minecraft:knowledge_book",
            "minecraft:written_book", "minecraft:filled_map", "minecraft:enchanted_book");
    private static final Set<String> SOFT_ENTITIES = Set.of(
            "minecraft:item", "minecraft:experience_orb", "minecraft:falling_block", "minecraft:lightning_bolt",
            "minecraft:marker", "minecraft:area_effect_cloud", "minecraft:fishing_bobber", "minecraft:arrow",
            "minecraft:spectral_arrow", "minecraft:trident", "minecraft:item_frame", "minecraft:glow_item_frame",
            "minecraft:painting", "minecraft:leash_knot", "minecraft:tnt", "minecraft:firework_rocket",
            "minecraft:end_crystal", "minecraft:ender_dragon", "minecraft:wither", "minecraft:villager",
            "minecraft:wandering_trader", "minecraft:interaction", "minecraft:block_display",
            "minecraft:item_display", "minecraft:text_display");

    public enum Level { NONE, SOFT, HARD }

    private Protection() {
    }

    public static Level of(ContentType type, String id) {
        if (id.startsWith("scalpel:")) {
            return Level.HARD;
        }
        return switch (type) {
            case BLOCK -> HARD_BLOCKS.contains(id) ? Level.HARD : SOFT_BLOCKS.contains(id) ? Level.SOFT : Level.NONE;
            case ITEM -> HARD_ITEMS.contains(id) ? Level.HARD : SOFT_ITEMS.contains(id) ? Level.SOFT : Level.NONE;
            case ENTITY -> HARD_ENTITIES.contains(id) ? Level.HARD : SOFT_ENTITIES.contains(id) ? Level.SOFT : Level.NONE;
            default -> Level.NONE;
        };
    }
}
