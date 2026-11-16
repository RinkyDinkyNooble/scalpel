package com.rinkynooble.scalpel.fabric;

import com.rinkynooble.scalpel.common.Platform;
import com.rinkynooble.scalpel.common.Scalpel;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.recipe.v1.ingredient.FabricIngredient;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FlowerPotBlock;
import com.rinkynooble.scalpel.fabric.mixin.FlowerPotBlockAccessor;

import java.nio.file.Path;
import java.util.Map;

/** Fabric (and Quilt, which runs Fabric mods) for Minecraft 1.20 and 1.20.1: vanilla's registries, Fabric's metadata. */
public final class FabricPlatform implements Platform {
    static volatile Item removedItem;
    static volatile Block removedBlock;

    @Override
    public String port() {
        return "fabric-1.20.1";
    }

    @Override
    public Path configDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public Path gameDir() {
        return FabricLoader.getInstance().getGameDir();
    }

    @Override
    public String modVersion() {
        return FabricLoader.getInstance().getModContainer(Scalpel.MOD_ID)
                .map(mod -> mod.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }

    @Override
    public boolean isClient() {
        return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;
    }

    @Override
    public Object registeredValue(ResourceKey<? extends Registry<?>> registry, ResourceLocation name) {
        Registry<?> found = BuiltInRegistries.REGISTRY.get(registry.location());
        return found != null && found.containsKey(name) ? found.get(name) : null;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public ResourceLocation registeredKey(ResourceKey<? extends Registry<?>> registry, Object value) {
        Registry found = BuiltInRegistries.REGISTRY.get(registry.location());
        if (found == null) {
            return null;
        }
        // getResourceKey, not getKey: a defaulted registry answers getKey with its default id for unknown values.
        return ((java.util.Optional<ResourceKey<?>>) found.getResourceKey(value)).map(ResourceKey::location).orElse(null);
    }

    @Override
    public String spawnEggEntityId(SpawnEggItem egg) {
        return null;
    }

    @Override
    public void forgetSpawnEgg(SpawnEggItem egg) {
        // Fabric keeps no egg list of its own; vanilla's is handled by the shared code.
    }

    @Override
    public void forgetFlowerPot(FlowerPotBlock pot, Block replacement) {
        // Vanilla keeps one map from plant to potted block, filled by each pot's constructor.
        Map<Block, Block> potted = FlowerPotBlockAccessor.scalpel$pottedByContent();
        if (replacement != null) {
            potted.replaceAll((plant, block) -> block == pot ? replacement : block);
        } else {
            potted.values().removeIf(block -> block == pot);
        }
    }

    @Override
    public Item removedItem() {
        return removedItem;
    }

    @Override
    public Block removedBlock() {
        return removedBlock;
    }

    @Override
    public boolean isVanillaIngredient(Ingredient ingredient) {
        return ((FabricIngredient) (Object) ingredient).getCustomIngredient() == null;
    }
}
