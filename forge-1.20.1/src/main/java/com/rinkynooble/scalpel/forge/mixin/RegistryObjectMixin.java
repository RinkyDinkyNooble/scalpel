package com.rinkynooble.scalpel.forge.mixin;

import com.rinkynooble.scalpel.forge.registry.RegistryCutter;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.RegistryObject;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A removed entry resolves to its unregistered ghost instead of throwing. */
@Mixin(value = RegistryObject.class, remap = false)
public abstract class RegistryObjectMixin<T> {
    @Shadow
    @Final
    private ResourceLocation name;

    @Shadow
    private ResourceKey<T> key;

    @Shadow
    private T value;

    @Inject(method = "get()Ljava/lang/Object;", at = @At("HEAD"), cancellable = true)
    @SuppressWarnings("unchecked")
    private void scalpel$softLanding(CallbackInfoReturnable<T> cir) {
        if (value == null && key != null) {
            Object ghost = RegistryCutter.ghostFor(key.registry(), name);
            if (ghost != null) {
                cir.setReturnValue((T) ghost);
            }
        }
    }
}
