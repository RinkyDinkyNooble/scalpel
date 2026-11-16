package com.rinkynooble.scalpel.fabric.mixin;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FlowerPotBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/** Vanilla's plant to potted-block map, which a refused pot's constructor wrote itself into. */
@Mixin(FlowerPotBlock.class)
public interface FlowerPotBlockAccessor {
    @Accessor("POTTED_BY_CONTENT")
    static Map<Block, Block> scalpel$pottedByContent() {
        throw new AssertionError();
    }
}
