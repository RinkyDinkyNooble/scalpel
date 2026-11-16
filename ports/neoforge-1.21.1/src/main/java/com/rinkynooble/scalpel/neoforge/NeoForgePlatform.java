package com.rinkynooble.scalpel.neoforge;

import com.rinkynooble.scalpel.common.Platform;
import com.rinkynooble.scalpel.common.Scalpel;
import com.rinkynooble.scalpel.neoforge.mixin.DeferredSpawnEggItemAccessor;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import java.util.function.Supplier;

/** NeoForge 1.21.1: registries are vanilla's (NeoForge has no registry of its own since 1.20.2). */
public final class NeoForgePlatform implements Platform {
    @Override
    public String port() {
        return "neoforge-1.21.1";
    }

    @Override
    public Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public Path gameDir() {
        return FMLPaths.GAMEDIR.get();
    }

    @Override
    public String modVersion() {
        try {
            return FMLLoader.getLoadingModList().getModFileById(Scalpel.MOD_ID).getMods().get(0).getVersion().toString();
        } catch (RuntimeException e) {
            return "unknown";
        }
    }

    @Override
    public boolean isClient() {
        return FMLEnvironment.dist == Dist.CLIENT;
    }

    @Override
    public Object registeredValue(ResourceKey<? extends Registry<?>> registry, ResourceLocation name) {
        Registry<?> found = BuiltInRegistries.REGISTRY.get(registry.location());
        return found == null ? null : found.get(name);
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public ResourceLocation registeredKey(ResourceKey<? extends Registry<?>> registry, Object value) {
        Registry found = BuiltInRegistries.REGISTRY.get(registry.location());
        return found == null ? null : found.getKey(value);
    }

    @Override
    public String spawnEggEntityId(SpawnEggItem egg) {
        if (!(egg instanceof DeferredSpawnEggItem deferredEgg)) {
            return null;
        }
        Supplier<? extends EntityType<?>> supplier = ((DeferredSpawnEggItemAccessor) deferredEgg).scalpel$typeSupplier();
        if (supplier instanceof DeferredHolder<?, ?> holder) {
            return holder.getId().toString();
        }
        ResourceLocation key = registeredKey(Registries.ENTITY_TYPE, supplier.get());
        return key == null ? null : key.toString();
    }

    @Override
    public void forgetSpawnEgg(SpawnEggItem egg) {
        if (egg instanceof DeferredSpawnEggItem deferredEgg) {
            DeferredSpawnEggItemAccessor.scalpel$modEggs().remove(deferredEgg);
            DeferredSpawnEggItemAccessor.scalpel$typeMap().values().removeIf(e -> e == deferredEgg);
        }
    }

    @Override
    public void forgetFlowerPot(FlowerPotBlock pot, Block replacement) {
        if (!(Blocks.FLOWER_POT instanceof FlowerPotBlock empty)) {
            return;
        }
        Block stand = replacement != null ? replacement : Blocks.AIR;
        for (Map.Entry<ResourceLocation, Supplier<? extends Block>> plant : new ArrayList<>(empty.getFullPotsView().entrySet())) {
            try {
                if (plant.getValue().get() == pot) {
                    empty.addPlant(plant.getKey(), () -> stand);
                }
            } catch (RuntimeException e) {
                // Another mod's pot that isn't registered yet; it isn't this one.
            }
        }
    }

    @Override
    public Item removedItem() {
        return NeoForgeRemoved.ITEM.isBound() ? NeoForgeRemoved.ITEM.get() : null;
    }

    @Override
    public Block removedBlock() {
        return NeoForgeRemoved.BLOCK.isBound() ? NeoForgeRemoved.BLOCK.get() : null;
    }

    @Override
    public boolean isVanillaIngredient(Ingredient ingredient) {
        return !ingredient.isCustom();
    }
}
