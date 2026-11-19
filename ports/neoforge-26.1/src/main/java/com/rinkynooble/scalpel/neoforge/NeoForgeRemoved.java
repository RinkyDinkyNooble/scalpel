package com.rinkynooble.scalpel.neoforge;

import com.rinkynooble.scalpel.common.Scalpel;
import com.rinkynooble.scalpel.common.content.RemovedItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registers {@code scalpel:removed} (item and block, see {@link RemovedItem}) through NeoForge's DeferredRegister. */
public final class NeoForgeRemoved {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, Scalpel.MOD_ID);
    static final DeferredHolder<Item, Item> ITEM = ITEMS.register(RemovedItem.PATH, RemovedItem::newItem);
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, Scalpel.MOD_ID);
    static final DeferredHolder<Block, Block> BLOCK = BLOCKS.register(RemovedItem.PATH, RemovedItem::newBlock);

    private NeoForgeRemoved() {
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        BLOCKS.register(modBus);
    }
}
