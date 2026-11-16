package com.rinkynooble.scalpel.forge;

import com.rinkynooble.scalpel.common.Scalpel;
import com.rinkynooble.scalpel.common.content.RemovedItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Registers {@code scalpel:removed} (item and block, see {@link RemovedItem}) through Forge's DeferredRegister. */
public final class ForgeRemoved {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Scalpel.MOD_ID);
    static final RegistryObject<Item> ITEM = ITEMS.register(RemovedItem.PATH, RemovedItem::newItem);
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Scalpel.MOD_ID);
    static final RegistryObject<Block> BLOCK = BLOCKS.register(RemovedItem.PATH, RemovedItem::newBlock);

    private ForgeRemoved() {
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        BLOCKS.register(modBus);
    }
}
