package com.rinkynooble.scalpel.common;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FlowerPotBlock;

import java.nio.file.Path;

/**
 * What the shared code needs from the mod loader. Each port has one implementation, found with {@link java.util.ServiceLoader}
 * through {@code META-INF/services}, so it works before any of the loader's own setup runs (Scalpel starts at the first
 * registry entry, during vanilla's bootstrap).
 */
public interface Platform {
    /** {@code forge-1.20.1}, {@code fabric-1.20.1}: shown in logs and the report. */
    String port();

    Path configDir();

    Path gameDir();

    /** This mod's version from the loader's metadata, or {@code unknown}. */
    String modVersion();

    boolean isClient();

    /** The object registered under {@code name} right now, or null. Called while registries are still open. */
    Object registeredValue(ResourceKey<? extends Registry<?>> registry, ResourceLocation name);

    /** The id {@code value} is registered under right now, or null. */
    ResourceLocation registeredKey(ResourceKey<? extends Registry<?>> registry, Object value);

    /** The entity id a loader-specific spawn egg spawns, or null to fall back to vanilla's field. */
    String spawnEggEntityId(SpawnEggItem egg);

    /** Takes a refused spawn egg out of the loader's own egg lists (vanilla's list is handled by the caller). */
    void forgetSpawnEgg(SpawnEggItem egg);

    /**
     * A refused flower pot: whatever plant it holds must no longer pot into it. {@code replacement} is the placeholder
     * it was swapped for, or null when it was removed (the plant then can't be potted).
     */
    void forgetFlowerPot(FlowerPotBlock pot, Block replacement);

    /** The registered {@code scalpel:removed} item, or null before it is registered. */
    Item removedItem();

    /** The registered {@code scalpel:removed} block, or null before it is registered. */
    Block removedBlock();

    /** True for vanilla's item and tag ingredients, false for a loader's or mod's custom ingredient types. */
    boolean isVanillaIngredient(Ingredient ingredient);
}
