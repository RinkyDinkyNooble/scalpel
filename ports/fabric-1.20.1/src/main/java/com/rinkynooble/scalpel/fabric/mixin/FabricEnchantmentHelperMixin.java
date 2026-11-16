package com.rinkynooble.scalpel.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.rinkynooble.scalpel.common.data.CutEnchantments;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Optional;

/**
 * Vanilla walks an item's enchantment list itself for damage bonuses, protection and post-attack effects (Forge goes
 * through {@code deserializeEnchantments} instead). A redacted enchantment is skipped there, so it does nothing.
 */
@Mixin(EnchantmentHelper.class)
public abstract class FabricEnchantmentHelperMixin {
    @WrapOperation(method = "runIterationOnItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/core/Registry;getOptional(Lnet/minecraft/resources/ResourceLocation;)Ljava/util/Optional;"))
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Optional scalpel$skipInert(Registry registry, ResourceLocation id, Operation<Optional> original) {
        Optional found = original.call(registry, id);
        return found.filter(value -> !(value instanceof Enchantment enchantment && CutEnchantments.isInert(enchantment)));
    }
}
