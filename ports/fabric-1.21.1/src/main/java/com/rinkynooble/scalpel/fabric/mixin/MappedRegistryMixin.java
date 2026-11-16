package com.rinkynooble.scalpel.fabric.mixin;

import com.rinkynooble.scalpel.common.registry.RegistryCutter;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * Every registration ends here on Fabric: vanilla's own content and every mod's {@code Registry.register} call. Fabric
 * has no lazy registration, so the object always exists already; a refused one is left unregistered (its intrusive
 * holder released), a redacted one is swapped for its placeholder.
 */
@Mixin(MappedRegistry.class)
public abstract class MappedRegistryMixin<T> {
    @Shadow
    private boolean frozen;

    @Shadow
    public abstract ResourceKey<? extends Registry<T>> key();

    @Shadow
    public abstract HolderOwner<T> holderOwner();

    @Shadow
    public abstract Holder.Reference<T> register(ResourceKey<T> key, T value, RegistrationInfo info);

    @Inject(method = "register(Lnet/minecraft/resources/ResourceKey;Ljava/lang/Object;Lnet/minecraft/core/RegistrationInfo;)Lnet/minecraft/core/Holder$Reference;",
            at = @At("HEAD"), cancellable = true)
    @SuppressWarnings("unchecked")
    private void scalpel$beforeRegister(ResourceKey<T> key, T value, RegistrationInfo info, CallbackInfoReturnable<Holder.Reference<T>> cir) {
        if (frozen || value == null) {
            return;
        }
        RegistryCutter.Cut cut = RegistryCutter.beforeAdd(key(), key.location(), value, null);
        if (cut == null) {
            return;
        }
        if (cut.skip()) {
            // Callers of Registry.register ignore this; it stays unbound, like the refused object.
            cir.setReturnValue(Holder.Reference.createStandAlone(holderOwner(), key));
            return;
        }
        cir.setReturnValue(register(key, (T) cut.replacement(), info));
    }

    /*
     * A refused object a mod kept (on Fabric, mods always build their own content) has no key, id or registry entry.
     * Vanilla then reads it as air: saved stacks lose their item, recipe viewers reject it, network writes fail. These
     * three lookups answer with its stand-in instead, the way the Forge port answers Forge's delegate lookup: a redacted
     * original reads as its placeholder, a removed one as scalpel:removed. Only empty answers are touched, so lookups
     * of registered values cost nothing extra.
     *
     * 1.21 has no freeze hook here: enchantments are data now, so no unbound holder of a removed one is left behind.
     */

    @Inject(method = "getKey", at = @At("RETURN"), cancellable = true)
    @SuppressWarnings("unchecked")
    private void scalpel$standInKey(T value, CallbackInfoReturnable<ResourceLocation> cir) {
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

    @Inject(method = "getId", at = @At("RETURN"), cancellable = true)
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
