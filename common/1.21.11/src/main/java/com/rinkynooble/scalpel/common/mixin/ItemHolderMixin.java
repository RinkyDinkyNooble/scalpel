package com.rinkynooble.scalpel.common.mixin;

import com.rinkynooble.scalpel.common.registry.RegistryCutter;
import net.minecraft.core.Holder;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A refused item a mod kept (its own object, which its holder still answers) has a registry holder with no key, and
 * since 1.20.5 a stack is saved by that holder ("Unregistered holder in minecraft:item"). Its holder reads as its
 * stand-in's: a redacted original's stack is the placeholder's, a removed one's is {@code scalpel:removed}. Registered
 * items have a bound holder and skip this after one check.
 */
@Mixin(Item.class)
public abstract class ItemHolderMixin {
    @Inject(method = "builtInRegistryHolder", at = @At("RETURN"), cancellable = true)
    private void scalpel$standInHolder(CallbackInfoReturnable<Holder.Reference<Item>> cir) {
        Holder.Reference<Item> holder = cir.getReturnValue();
        if (holder != null && !holder.isBound() && RegistryCutter.standInFor(this) instanceof Item standIn && standIn != (Object) this) {
            cir.setReturnValue(standIn.builtInRegistryHolder());
        }
    }
}
