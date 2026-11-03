package com.rinkynooble.scalpel.forge.mixin;

import com.rinkynooble.scalpel.forge.data.CutEnchantments;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Map;

/**
 * Where a redacted enchantment goes inert. Every effect either reads a level, or walks what an item says it has
 * (damage bonuses, protection, post-attack effects), so these two are enough. The enchanting table's list is filtered
 * here because a mod's enchantment class can override every method that would otherwise keep it off the table.
 */
@Mixin(EnchantmentHelper.class)
public abstract class EnchantmentHelperMixin {
    @Inject(method = "getTagEnchantmentLevel", at = @At("HEAD"), cancellable = true, remap = false)
    private static void scalpel$inertTagLevel(Enchantment enchantment, ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        if (CutEnchantments.isInert(enchantment)) {
            cir.setReturnValue(0);
        }
    }

    /** Runs before the item's own {@code getEnchantmentLevel}, which a mod's item can override. */
    @Inject(method = "getItemEnchantmentLevel", at = @At("HEAD"), cancellable = true)
    private static void scalpel$inertItemLevel(Enchantment enchantment, ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        if (CutEnchantments.isInert(enchantment)) {
            cir.setReturnValue(0);
        }
    }

    @Inject(method = "deserializeEnchantments", at = @At("RETURN"))
    private static void scalpel$withoutInert(ListTag list, CallbackInfoReturnable<Map<Enchantment, Integer>> cir) {
        cir.getReturnValue().keySet().removeIf(CutEnchantments::isInert);
    }

    @Inject(method = "getAvailableEnchantmentResults", at = @At("RETURN"))
    private static void scalpel$offerNoInert(int level, ItemStack stack, boolean treasure, CallbackInfoReturnable<List<EnchantmentInstance>> cir) {
        cir.getReturnValue().removeIf(instance -> CutEnchantments.isInert(instance.enchantment));
    }
}
