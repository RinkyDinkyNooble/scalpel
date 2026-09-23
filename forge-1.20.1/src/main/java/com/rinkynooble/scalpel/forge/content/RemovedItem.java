package com.rinkynooble.scalpel.forge.content;

import com.rinkynooble.scalpel.forge.Scalpel;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * {@code scalpel:removed}: one hidden item handed to mod code that still asks for a removed item.
 * Unlike blocks and entity types, an item has to be registered to go into an {@code ItemStack}, which mods
 * commonly build from their own items during setup. Hidden from creative tabs and recipe viewers.
 */
public final class RemovedItem {
    public static final String ID = Scalpel.MOD_ID + ":removed";

    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Scalpel.MOD_ID);
    public static final RegistryObject<Item> ITEM = ITEMS.register("removed", () -> new Item(new Item.Properties()));

    private RemovedItem() {
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }

    public static Item getOrNull() {
        return ITEM.isPresent() ? ITEM.get() : null;
    }
}
