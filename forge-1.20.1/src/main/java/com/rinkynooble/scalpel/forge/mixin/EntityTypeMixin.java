package com.rinkynooble.scalpel.forge.mixin;

import com.rinkynooble.scalpel.forge.registry.RegistryCutter;
import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Vanilla keeps its entity types in static fields; point those fields at the placeholder when one is redacted. */
@Mixin(EntityType.class)
public abstract class EntityTypeMixin {
    @Inject(method = "register(Ljava/lang/String;Lnet/minecraft/world/entity/EntityType$Builder;)Lnet/minecraft/world/entity/EntityType;",
            at = @At("RETURN"), cancellable = true)
    private static void scalpel$usePlaceholder(String id, EntityType.Builder<?> builder, CallbackInfoReturnable<EntityType<?>> cir) {
        Object placeholder = RegistryCutter.placeholderFor(cir.getReturnValue());
        if (placeholder != null) {
            cir.setReturnValue((EntityType<?>) placeholder);
        }
    }
}
