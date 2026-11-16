package com.rinkynooble.scalpel.common.mixin;

import com.rinkynooble.scalpel.common.data.CutEnchantments;
import net.minecraft.core.Holder;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Since 1.21 loot conditions read an item's enchantment levels straight from its component (silk touch on ores:
 * {@code match_tool} with an enchantment predicate), not through {@code EnchantmentHelper}. A redacted enchantment
 * reads level 0 here too, as it did on 1.20.1. The tooltip still lists it, named "Redacted".
 */
@Mixin(ItemEnchantments.class)
public abstract class ItemEnchantmentsMixin {
    @Inject(method = "getLevel", at = @At("HEAD"), cancellable = true)
    private void scalpel$inertLevel(Holder<Enchantment> enchantment, CallbackInfoReturnable<Integer> cir) {
        if (CutEnchantments.isInert(enchantment)) {
            cir.setReturnValue(0);
        }
    }
}
