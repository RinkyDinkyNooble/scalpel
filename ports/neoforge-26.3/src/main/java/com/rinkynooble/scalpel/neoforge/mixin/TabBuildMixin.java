package com.rinkynooble.scalpel.neoforge.mixin;

import com.rinkynooble.scalpel.common.content.RemovedItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.event.EventHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * 26.1: a stack holds its item's registry holder, and a removed item a mod kept reads as {@code scalpel:removed}
 * (ItemHolderMixin). A mod that lists two of its removed items in its tab therefore lists {@code scalpel:removed} twice.
 * NeoForge's tab builder collects entries in a set, so production just keeps one (and Scalpel's tab filter takes it out),
 * but in a dev environment NeoForge throws "has already been added to the tab" (seen with Macaw's Furniture, JEI then
 * left the whole tab out), and its source says the check becomes unconditional in 26.3. Only the repeats of
 * {@code scalpel:removed} are skipped before NeoForge sees them (the one left is taken out by the tab filter anyway);
 * every other stack is passed on as is.
 */
@Mixin(EventHooks.class)
public abstract class TabBuildMixin {
    @ModifyArg(method = "onCreativeModeTabBuildContents",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/CreativeModeTab$DisplayItemsGenerator;accept(Lnet/minecraft/world/item/CreativeModeTab$ItemDisplayParameters;Lnet/minecraft/world/item/CreativeModeTab$Output;)V"),
            index = 1, require = 0)
    private static CreativeModeTab.Output scalpel$removedOnce(CreativeModeTab.Output output) {
        Item removed = RemovedItem.getOrNull();
        if (removed == null) {
            return output;
        }
        boolean[] seen = {false};
        return (stack, visibility) -> {
            if (!stack.isEmpty() && stack.getItem() == removed) {
                if (seen[0]) {
                    return;
                }
                seen[0] = true;
            }
            output.accept(stack, visibility);
        };
    }
}
