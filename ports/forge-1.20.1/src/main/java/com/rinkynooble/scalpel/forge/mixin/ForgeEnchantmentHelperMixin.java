package com.rinkynooble.scalpel.forge.mixin;

import com.rinkynooble.scalpel.common.data.CutEnchantments;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Forge's own level lookup, which its item stacks use: a redacted enchantment reads level 0 there too. */
@Mixin(EnchantmentHelper.class)
public abstract class ForgeEnchantmentHelperMixin {
    @Inject(method = "getTagEnchantmentLevel", at = @At("HEAD"), cancellable = true, remap = false)
    private static void scalpel$inertTagLevel(Enchantment enchantment, ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        if (CutEnchantments.isInert(enchantment)) {
            cir.setReturnValue(0);
        }
    }
}
