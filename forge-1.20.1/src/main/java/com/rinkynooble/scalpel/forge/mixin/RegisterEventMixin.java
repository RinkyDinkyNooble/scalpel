package com.rinkynooble.scalpel.forge.mixin;

import com.rinkynooble.scalpel.forge.registry.RegistryCutter;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistry;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.RegisterEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Supplier;

/** Cuts DeferredRegister entries before their supplier runs, so the mod's object is never built. */
@Mixin(value = RegisterEvent.class, remap = false)
public abstract class RegisterEventMixin {
    @Shadow
    @Final
    private ResourceKey<? extends Registry<?>> registryKey;

    @Shadow
    @Final
    ForgeRegistry<?> forgeRegistry;

    @Inject(method = "register(Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/resources/ResourceLocation;Ljava/util/function/Supplier;)V",
            at = @At("HEAD"), cancellable = true)
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void scalpel$beforeRegister(ResourceKey<?> key, ResourceLocation name, Supplier<?> valueSupplier, CallbackInfo ci) {
        if (forgeRegistry == null || !registryKey.equals(key) || RegistryCutter.wrapsBuiltObject(valueSupplier)) {
            return;
        }
        RegistryCutter.Cut cut = RegistryCutter.beforeLazyRegister(key, name);
        if (cut == null) {
            return;
        }
        ci.cancel();
        if (!cut.skip()) {
            ((IForgeRegistry) forgeRegistry).register(name, cut.replacement());
        }
    }
}
