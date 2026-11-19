package com.rinkynooble.scalpel.neoforge.mixin;

import com.rinkynooble.scalpel.common.registry.RegistryCutter;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * Every registration ends here on NeoForge since 1.20.2 (NeoForge has no registry of its own): vanilla's content,
 * DeferredRegister entries the lazy hook let through, and direct {@code Registry.register} calls. The object exists
 * here, so links (a block item's block, a spawn egg's entity) can be checked. A refused object is left unregistered
 * (its intrusive holder released), a redacted one is swapped for its placeholder.
 */
@Mixin(MappedRegistry.class)
public abstract class MappedRegistryMixin<T> {
    @Shadow
    private boolean frozen;

    @Shadow
    public abstract ResourceKey<? extends Registry<T>> key();

    @Shadow(remap = false)
    public abstract Holder.Reference<T> register(int id, ResourceKey<T> key, T value, RegistrationInfo info);

    @Inject(method = "register(ILnet/minecraft/resources/ResourceKey;Ljava/lang/Object;Lnet/minecraft/core/RegistrationInfo;)Lnet/minecraft/core/Holder$Reference;",
            at = @At("HEAD"), cancellable = true, remap = false)
    @SuppressWarnings("unchecked")
    private void scalpel$beforeRegister(int id, ResourceKey<T> key, T value, RegistrationInfo info, CallbackInfoReturnable<Holder.Reference<T>> cir) {
        if (frozen || value == null) {
            return;
        }
        RegistryCutter.Cut cut = RegistryCutter.beforeAdd(key(), key.identifier(), value, null);
        if (cut == null) {
            return;
        }
        if (cut.skip()) {
            // Callers of Registry.register ignore this; it stays unbound, like the refused object.
            // Since 1.21.2 the registry is its own holder owner (no holderOwner()).
            cir.setReturnValue(Holder.Reference.createStandAlone((HolderOwner<T>) (Object) this, key));
            return;
        }
        cir.setReturnValue(register(id, key, (T) cut.replacement(), info));
    }

    /*
     * A refused object a mod kept (one registered with Registry.register or RegisterEvent's helper rather than a
     * DeferredRegister supplier) has no key, id or registry entry, and vanilla reads it as air. These lookups answer
     * with its stand-in instead: a redacted original reads as its placeholder, a removed one as scalpel:removed. Only
     * empty answers are touched, so lookups of registered values cost nothing extra.
     */

    @Inject(method = "getKey", at = @At("RETURN"), cancellable = true)
    @SuppressWarnings("unchecked")
    private void scalpel$standInKey(T value, CallbackInfoReturnable<Identifier> cir) {
        if (cir.getReturnValue() == null) {
            Object standIn = RegistryCutter.standInFor(value);
            if (standIn != null && standIn != value) {
                cir.setReturnValue(((Registry<T>) (Object) this).getKey((T) standIn));
            }
        }
    }

    @Inject(method = "getResourceKey", at = @At("RETURN"), cancellable = true)
    @SuppressWarnings("unchecked")
    private void scalpel$standInResourceKey(T value, CallbackInfoReturnable<Optional<ResourceKey<T>>> cir) {
        if (cir.getReturnValue().isEmpty()) {
            Object standIn = RegistryCutter.standInFor(value);
            if (standIn != null && standIn != value) {
                cir.setReturnValue(((Registry<T>) (Object) this).getResourceKey((T) standIn));
            }
        }
    }

    @Inject(method = "getId(Ljava/lang/Object;)I", at = @At("RETURN"), cancellable = true)
    @SuppressWarnings("unchecked")
    private void scalpel$standInId(T value, CallbackInfoReturnable<Integer> cir) {
        if (cir.getReturnValueI() == -1) {
            Object standIn = RegistryCutter.standInFor(value);
            if (standIn != null && standIn != value) {
                cir.setReturnValue(((Registry<T>) (Object) this).getId((T) standIn));
            }
        }
    }
}
