package com.rinkynooble.scalpel.forge.mixin;

import com.rinkynooble.scalpel.forge.data.StructureFilter;
import net.minecraft.core.HolderGetter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Structure palettes (and a few saved entities) store block states by id; a cut block reads as air. */
@Mixin(NbtUtils.class)
public abstract class NbtUtilsMixin {
    @Inject(method = "readBlockState", at = @At("HEAD"), cancellable = true)
    private static void scalpel$cutBlockIsAir(HolderGetter<Block> blocks, CompoundTag tag, CallbackInfoReturnable<BlockState> cir) {
        if (StructureFilter.isCutBlock(tag)) {
            cir.setReturnValue(Blocks.AIR.defaultBlockState());
        }
    }
}
