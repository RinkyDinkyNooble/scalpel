package com.rinkynooble.scalpel.forge;

import com.rinkynooble.scalpel.common.Platform;
import com.rinkynooble.scalpel.common.Scalpel;
import com.rinkynooble.scalpel.forge.mixin.ForgeSpawnEggItemAccessor;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistry;
import net.minecraftforge.registries.RegistryManager;
import net.minecraftforge.registries.RegistryObject;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import java.util.function.Supplier;

/** Forge 1.20.1: registry lookups go to Forge's active registries, the way Scalpel 0.3.0 did them. */
public final class ForgePlatform implements Platform {
    @Override
    public String port() {
        return "forge-1.20.1";
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
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Object registeredValue(ResourceKey<? extends Registry<?>> registry, ResourceLocation name) {
        ForgeRegistry forgeRegistry = RegistryManager.ACTIVE.getRegistry((ResourceKey) registry);
        return forgeRegistry == null ? null : forgeRegistry.getValue(name);
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public ResourceLocation registeredKey(ResourceKey<? extends Registry<?>> registry, Object value) {
        ForgeRegistry forgeRegistry = RegistryManager.ACTIVE.getRegistry((ResourceKey) registry);
        if (forgeRegistry == null) {
            return null;
        }
        return ((java.util.Optional<ResourceKey<?>>) forgeRegistry.getResourceKey(value)).map(ResourceKey::location).orElse(null);
    }

    @Override
    public String spawnEggEntityId(SpawnEggItem egg) {
        if (!(egg instanceof ForgeSpawnEggItem forgeEgg)) {
            return null;
        }
        Supplier<? extends EntityType<?>> supplier = ((ForgeSpawnEggItemAccessor) forgeEgg).scalpel$typeSupplier();
        if (supplier instanceof RegistryObject<?> registryObject) {
            return registryObject.getId().toString();
        }
        ResourceLocation key = registeredKey(net.minecraft.core.registries.Registries.ENTITY_TYPE, supplier.get());
        return key == null ? null : key.toString();
    }

    @Override
    public void forgetSpawnEgg(SpawnEggItem egg) {
        if (egg instanceof ForgeSpawnEggItem forgeEgg) {
            ForgeSpawnEggItemAccessor.scalpel$modEggs().remove(forgeEgg);
            ForgeSpawnEggItemAccessor.scalpel$typeMap().values().removeIf(e -> e == forgeEgg);
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
        return ForgeRemoved.ITEM.isPresent() ? ForgeRemoved.ITEM.get() : null;
    }

    @Override
    public Block removedBlock() {
        return ForgeRemoved.BLOCK.isPresent() ? ForgeRemoved.BLOCK.get() : null;
    }

    @Override
    public boolean isVanillaIngredient(Ingredient ingredient) {
        return ingredient.isVanilla();
    }
}
