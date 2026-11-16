package com.rinkynooble.scalpel.fabric.mixin;

import com.rinkynooble.scalpel.common.Scalpel;
import net.minecraft.core.registries.BuiltInRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Vanilla freezes its registries once every mod has registered its content: registration is over. */
@Mixin(BuiltInRegistries.class)
public abstract class BuiltInRegistriesMixin {
    @Inject(method = "freeze", at = @At("TAIL"))
    private static void scalpel$registrationDone(CallbackInfo ci) {
        Scalpel.afterRegistration();
    }
}
