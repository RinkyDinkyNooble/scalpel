package com.rinkynooble.scalpel.forge.mixin;

import com.rinkynooble.scalpel.forge.registry.RegistryCutter;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Vanilla keeps its items in static fields; point those fields at the placeholder when an item is redacted. */
@Mixin(Items.class)
public abstract class ItemsMixin {
    @Inject(method = "registerItem(Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/world/item/Item;)Lnet/minecraft/world/item/Item;",
            at = @At("RETURN"), cancellable = true)
    private static void scalpel$usePlaceholder(ResourceKey<Item> key, Item item, CallbackInfoReturnable<Item> cir) {
        Object placeholder = RegistryCutter.placeholderFor(cir.getReturnValue());
        if (placeholder != null) {
            cir.setReturnValue((Item) placeholder);
        }
    }
}
