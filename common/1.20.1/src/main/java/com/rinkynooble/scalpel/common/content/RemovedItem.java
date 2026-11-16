package com.rinkynooble.scalpel.common.content;

import com.rinkynooble.scalpel.common.Scalpel;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * {@code scalpel:removed}: one hidden item handed to mod code that still asks for a removed item.
 * Unlike blocks and entity types, an item has to be registered to go into an {@code ItemStack}, which mods
 * commonly build from their own items during setup. Hidden from creative tabs and recipe viewers.
 *
 * <p>There is one registered block too, with no item. It is what the loader's lookups resolve to for a removed block
 * that a mod holds on to (see {@code RegistryCutter#standInFor}), such as render layers set on a block in a static field.
 * Each port registers both its own way.
 */
public final class RemovedItem {
    public static final String ID = Scalpel.MOD_ID + ":removed";
    public static final String PATH = "removed";

    private RemovedItem() {
    }

    public static Item newItem() {
        return new Item(new Item.Properties());
    }

    public static Block newBlock() {
        return new Block(BlockBehaviour.Properties.of().instabreak().noLootTable().sound(SoundType.STONE));
    }

    public static Item getOrNull() {
        return Scalpel.platform().removedItem();
    }

    public static Block blockOrNull() {
        return Scalpel.platform().removedBlock();
    }
}
