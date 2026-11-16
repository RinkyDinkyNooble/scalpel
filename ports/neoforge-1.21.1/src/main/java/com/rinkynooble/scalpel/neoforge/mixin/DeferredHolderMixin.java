package com.rinkynooble.scalpel.neoforge.mixin;

import com.rinkynooble.scalpel.common.registry.RegistryCutter;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * What a mod's DeferredHolder answers for an entry Scalpel cut. Mods cast what their holders give them to their own
 * classes (AE2's typed block arrays), so a refused block or item answers with the mod's own object, which Scalpel
 * built and kept: the registry holds the placeholder under the id, and lookups by the object answer the placeholder's
 * id (MappedRegistryMixin). A removed entity type answers its unregistered ghost instead of "Trying to access unbound
 * value". The answer is looked up once per holder, after its entry was decided.
 */
@Mixin(value = DeferredHolder.class, remap = false)
public abstract class DeferredHolderMixin<R, T extends R> {
    @Unique
    private static final Object scalpel$NONE = new Object();

    @Shadow
    @Final
    protected ResourceKey<R> key;

    @Shadow
    private Holder<R> holder;

    @Shadow
    protected abstract void bind(boolean throwOnMissingRegistry);

    /** The decided answer: an object, {@link #scalpel$NONE} for "the registry's own", or null when not decided yet. */
    @Unique
    private Object scalpel$answer;

    @Inject(method = "value()Ljava/lang/Object;", at = @At("HEAD"), cancellable = true)
    @SuppressWarnings("unchecked")
    private void scalpel$answer(CallbackInfoReturnable<T> cir) {
        Object answer = scalpel$answer;
        if (answer != null) {
            if (answer != scalpel$NONE) {
                cir.setReturnValue((T) answer);
            }
            return;
        }
        bind(false);
        if (holder != null && holder.isBound()) {
            // Registered: the entry is decided, so this is final.
            Object original = RegistryCutter.originalFor(key.registry(), key.location());
            scalpel$answer = original != null ? original : scalpel$NONE;
            if (original != null) {
                cir.setReturnValue((T) original);
            }
            return;
        }
        Object ghost = RegistryCutter.ghostFor(key.registry(), key.location());
        if (ghost != null) {
            scalpel$answer = ghost;
            cir.setReturnValue((T) ghost);
        }
    }
}
