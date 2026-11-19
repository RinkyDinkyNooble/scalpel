package com.rinkynooble.scalpel.forge;

import com.rinkynooble.scalpel.common.Platform;
import com.rinkynooble.scalpel.common.Scalpel;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.loading.LoadingModList;
import net.minecraftforge.registries.ForgeRegistry;
import net.minecraftforge.registries.RegistryManager;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/** Forge 62-64 (Minecraft 26.1-26.1.2): registry lookups go to Forge's active registries, as on earlier Forge. */
public final class ForgePlatform implements Platform {
    @Override
    public String port() {
        return "forge-26.1";
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
            return LoadingModList.getModFileById(Scalpel.MOD_ID).getMods().get(0).getVersion().toString();
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
    public Object registeredValue(ResourceKey<? extends Registry<?>> registry, Identifier name) {
        ForgeRegistry forgeRegistry = RegistryManager.ACTIVE.getRegistry((ResourceKey) registry);
        return forgeRegistry == null ? null : forgeRegistry.getValue(name);
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Identifier registeredKey(ResourceKey<? extends Registry<?>> registry, Object value) {
        ForgeRegistry forgeRegistry = RegistryManager.ACTIVE.getRegistry((ResourceKey) registry);
        if (forgeRegistry == null) {
            return null;
        }
        return ((Optional<ResourceKey<?>>) forgeRegistry.getResourceKey(value)).map(ResourceKey::identifier).orElse(null);
    }

    /** Forge 26.x has no spawn egg class of its own (eggs name their entity in a data component since 1.21.5). */
    @Override
    public String spawnEggEntityId(SpawnEggItem egg) {
        return null;
    }

    @Override
    public void forgetSpawnEgg(SpawnEggItem egg) {
    }

    @Override
    public void forgetFlowerPot(FlowerPotBlock pot, Block replacement) {
        if (!(Blocks.FLOWER_POT instanceof FlowerPotBlock empty)) {
            return;
        }
        Block stand = replacement != null ? replacement : Blocks.AIR;
        for (Map.Entry<Identifier, Supplier<? extends Block>> plant : new ArrayList<>(empty.getFullPotsView().entrySet())) {
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
