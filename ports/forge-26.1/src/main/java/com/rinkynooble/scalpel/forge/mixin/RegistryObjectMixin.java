package com.rinkynooble.scalpel.forge.mixin;

import com.rinkynooble.scalpel.common.registry.RegistryCutter;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraftforge.registries.RegistryObject;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * What a mod's RegistryObject answers for an entry Scalpel cut. Mods cast what they get to their own classes (typed
 * block arrays, a dispenser behaviour that needs its item's class), so a refused block or item answers with the mod's
 * own object, which Scalpel built and kept: the registry holds the placeholder under the id, and Forge's delegate
 * lookup by the object finds the placeholder ({@code ForgeRegistryMixin}). An entry that isn't registered at all (a
 * removed one Scalpel never built) answers its unregistered ghost instead of null.
 */
@Mixin(value = RegistryObject.class, remap = false)
public abstract class RegistryObjectMixin<T> {
    @Shadow
    @Final
    private Identifier name;

    @Shadow
    @Final
    private ResourceKey<T> key;

    @Shadow
    private T value;

    /** {registry's value, answer}: the answer worked out for that value (null = the value itself). get() is hot. */
    @Unique
    private Object[] scalpel$answer;

    @Inject(method = "get()Ljava/lang/Object;", at = @At("HEAD"), cancellable = true)
    @SuppressWarnings("unchecked")
    private void scalpel$answer(CallbackInfoReturnable<T> cir) {
        T current = value;
        if (key == null) {
            return;
        }
        if (current == null) {
            Object ghost = RegistryCutter.ghostFor(key.registry(), name);
            if (ghost != null) {
                cir.setReturnValue((T) ghost);
            }
            return;
        }
        Object[] cached = scalpel$answer;
        if (cached == null || cached[0] != current) {
            // Registered, so the entry is decided; worked out again only if Forge points this object at a new value.
            cached = new Object[]{current, RegistryCutter.originalFor(key.registry(), name)};
            scalpel$answer = cached;
        }
        if (cached[1] != null) {
            cir.setReturnValue((T) cached[1]);
        }
    }
}
