package com.rinkynooble.scalpel.forge.mixin;

import com.rinkynooble.scalpel.forge.data.TabsAndTrades;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;

/** A tab whose icon item is cut draws its first remaining item instead, once its contents are built. */
@Mixin(CreativeModeTab.class)
public abstract class CreativeModeTabMixin {
    @Shadow
    private ItemStack iconItemStack;

    @Unique
    private boolean scalpel$iconChecked;

    @Shadow
    public abstract Collection<ItemStack> getDisplayItems();

    @Inject(method = "getIconItem", at = @At("RETURN"), cancellable = true)
    private void scalpel$iconNotCut(CallbackInfoReturnable<ItemStack> cir) {
        if (scalpel$iconChecked) {
            return;
        }
        Collection<ItemStack> shown = getDisplayItems();
        if (shown.isEmpty()) {
            return;
        }
        scalpel$iconChecked = true;
        ItemStack icon = TabsAndTrades.iconFor((CreativeModeTab) (Object) this, cir.getReturnValue(), shown);
        if (icon != null) {
            iconItemStack = icon;
            cir.setReturnValue(icon);
        }
    }
}
