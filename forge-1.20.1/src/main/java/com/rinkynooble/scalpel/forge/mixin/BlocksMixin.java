package com.rinkynooble.scalpel.forge.mixin;

import com.rinkynooble.scalpel.forge.registry.RegistryCutter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Vanilla keeps its blocks in static fields; point those fields at the placeholder when a block is redacted. */
@Mixin(Blocks.class)
public abstract class BlocksMixin {
    @Inject(method = "register(Ljava/lang/String;Lnet/minecraft/world/level/block/Block;)Lnet/minecraft/world/level/block/Block;",
            at = @At("RETURN"), cancellable = true)
    private static void scalpel$usePlaceholder(String id, Block block, CallbackInfoReturnable<Block> cir) {
        Object placeholder = RegistryCutter.placeholderFor(cir.getReturnValue());
        if (placeholder != null) {
            cir.setReturnValue((Block) placeholder);
        }
    }
}
