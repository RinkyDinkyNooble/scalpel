package com.rinkynooble.scalpel.forge.content;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * The ghost of a removed block: never registered, handed to mod code that still asks for the block. Its item is
 * {@code scalpel:removed} rather than air, because mods fill their creative tabs with {@code block.asItem()} and Forge
 * refuses a tab entry of air ("The stack count must be 1"). The tab filter then takes the removed item out.
 */
public class RemovedBlock extends RedactedBlock {
    public RemovedBlock(ResourceLocation originalId) {
        super(originalId);
    }

    @Override
    public Item asItem() {
        Item removed = RemovedItem.getOrNull();
        return removed == null ? Items.AIR : removed;
    }
}
