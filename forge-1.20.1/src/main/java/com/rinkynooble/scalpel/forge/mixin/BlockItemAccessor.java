package com.rinkynooble.scalpel.forge.mixin;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The block a block item was built with. Forge's {@code getBlock()} throws when that block isn't registered. */
@Mixin(BlockItem.class)
public interface BlockItemAccessor {
    @Accessor("block")
    Block scalpel$block();
}
