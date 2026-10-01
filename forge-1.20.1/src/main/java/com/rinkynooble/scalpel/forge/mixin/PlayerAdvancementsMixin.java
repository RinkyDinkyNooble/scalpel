package com.rinkynooble.scalpel.forge.mixin;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.server.PlayerAdvancements;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mods often look an advancement up by id and pass the result straight in, with no null check. When the advancement
 * was removed, vanilla throws on the null and the server crashes. A missing advancement gets a blank progress that
 * is never done and has no criteria, and awarding or revoking it does nothing.
 */
@Mixin(PlayerAdvancements.class)
public abstract class PlayerAdvancementsMixin {
    @Inject(method = "getOrStartProgress", at = @At("HEAD"), cancellable = true)
    private void scalpel$missingProgress(Advancement advancement, CallbackInfoReturnable<AdvancementProgress> cir) {
        if (advancement == null) {
            cir.setReturnValue(new AdvancementProgress());
        }
    }

    @Inject(method = "award", at = @At("HEAD"), cancellable = true)
    private void scalpel$missingAward(Advancement advancement, String criterion, CallbackInfoReturnable<Boolean> cir) {
        if (advancement == null) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "revoke", at = @At("HEAD"), cancellable = true)
    private void scalpel$missingRevoke(Advancement advancement, String criterion, CallbackInfoReturnable<Boolean> cir) {
        if (advancement == null) {
            cir.setReturnValue(false);
        }
    }
}
