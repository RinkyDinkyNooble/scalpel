package com.rinkynooble.scalpel.forge.mixin;

import com.rinkynooble.scalpel.forge.registry.RegistryCutter;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

/**
 * A registry call that doesn't go through DeferredRegister (Fabric mods, for example) makes a holder for
 * the id whatever {@code ForgeRegistry#add} decides. A removed enchantment's holder is never bound, and Forge refuses to
 * freeze a registry with one. Blocks, items and entity types don't need this: their holders belong to the object.
 */
@Mixin(targets = "net.minecraftforge.registries.NamespacedWrapper", remap = false)
public abstract class NamespacedWrapperMixin<T> {
    @Shadow
    private Map<ResourceLocation, Holder.Reference<T>> holdersByName;

    @Inject(method = {"freeze", "m_203521_"}, at = @At("HEAD"))
    private void scalpel$forgetRemoved(CallbackInfoReturnable<Registry<T>> cir) {
        holdersByName.entrySet().removeIf(entry -> !entry.getValue().isBound() && RegistryCutter.isRemovedEnchantment(entry.getKey()));
    }
}
