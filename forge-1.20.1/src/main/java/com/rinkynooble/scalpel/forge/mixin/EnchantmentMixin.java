package com.rinkynooble.scalpel.forge.mixin;

import com.rinkynooble.scalpel.forge.data.CutEnchantments;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps a redacted enchantment from being picked at random by loot and villagers, and names it in tooltips. These are
 * the base methods, so a mod's class that overrides one skips this; {@code scalpel:strip_cut_items} and the trade
 * filter catch what still gets through.
 */
@Mixin(Enchantment.class)
public abstract class EnchantmentMixin {
    @Inject(method = "isDiscoverable", at = @At("HEAD"), cancellable = true)
    private void scalpel$notDiscoverable(CallbackInfoReturnable<Boolean> cir) {
        if (CutEnchantments.isInert((Enchantment) (Object) this)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "isTradeable", at = @At("HEAD"), cancellable = true)
    private void scalpel$notTradeable(CallbackInfoReturnable<Boolean> cir) {
        if (CutEnchantments.isInert((Enchantment) (Object) this)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "getFullname", at = @At("HEAD"), cancellable = true)
    private void scalpel$redactedName(int level, CallbackInfoReturnable<Component> cir) {
        if (CutEnchantments.isInert((Enchantment) (Object) this)) {
            cir.setReturnValue(Component.translatable("enchantment.scalpel.redacted").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
