package com.rinkynooble.scalpel.forge.mixin;

import com.rinkynooble.scalpel.common.registry.RegistryCutter;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A mod that builds its own blocks and keeps them (AE2 registers straight into the Forge registry; others hand over
 * objects they built earlier) still holds the block Scalpel refused, and its item was refused with it, so
 * {@code asItem()} answers air. Mods fill creative tabs from it, and an air entry makes the whole tab fail to build
 * ("Stack size must be exactly 1"). It answers the stand-in's item instead: the placeholder's for a redacted block,
 * {@code scalpel:removed} for a removed one, which the tab filter then leaves out.
 */
@Mixin(Block.class)
public abstract class BlockAsItemMixin {
    @Inject(method = "asItem", at = @At("RETURN"), cancellable = true)
    private void scalpel$standInItem(CallbackInfoReturnable<Item> cir) {
        if (cir.getReturnValue() == Items.AIR) {
            Item standIn = RegistryCutter.itemForRefusedBlock((Block) (Object) this);
            if (standIn != null) {
                cir.setReturnValue(standIn);
            }
        }
    }
}
