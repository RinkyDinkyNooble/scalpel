package com.rinkynooble.scalpel.common.mixin;

import com.rinkynooble.scalpel.common.data.CutEnchantments;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A redacted enchantment has no effects (its data was emptied, see {@code CutEnchantments}), but mods' code may still
 * read its level and act on it. Both level lookups read 0 for it, as on 1.20.1.
 */
@Mixin(EnchantmentHelper.class)
public abstract class EnchantmentHelperMixin {
    /** Runs before the loader's per-item level hook, which a mod's item can override. */
    @Inject(method = "getItemEnchantmentLevel", at = @At("HEAD"), cancellable = true)
    private static void scalpel$inertItemLevel(Holder<Enchantment> enchantment, ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        if (CutEnchantments.isInert(enchantment)) {
            cir.setReturnValue(0);
        }
    }

    /** NeoForge only: vanilla (Fabric) and Forge 52 have no such method, their lookups end in {@code ItemEnchantments#getLevel}. */
    @Inject(method = "getTagEnchantmentLevel", at = @At("HEAD"), cancellable = true, require = 0)
    private static void scalpel$inertTagLevel(Holder<Enchantment> enchantment, ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        if (CutEnchantments.isInert(enchantment)) {
            cir.setReturnValue(0);
        }
    }
}
