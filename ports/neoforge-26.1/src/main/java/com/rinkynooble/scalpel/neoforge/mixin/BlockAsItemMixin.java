package com.rinkynooble.scalpel.neoforge.mixin;

import com.rinkynooble.scalpel.common.registry.RegistryCutter;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A block Scalpel refused still exists in the mod that built it (a mod that registered it directly, not through a DeferredRegister supplier), and its item was
 * refused with it, so vanilla's {@code asItem()} answers air. Mods fill creative tabs and recipe viewer icons from it
 * (AE2's main tab and its JEI categories), and an air stack breaks both. It answers the stand-in's item instead.
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
