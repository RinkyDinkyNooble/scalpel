package com.rinkynooble.scalpel.common.mixin;

import com.rinkynooble.scalpel.common.data.ServerData;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The end of a server data load (1.21.2+). {@code finalizeRecipeLoading} runs on the main thread once every reload
 * listener is done and tags are bound (startup and /reload), and builds the stonecutter list, recipe book and what
 * clients receive from the recipe map. The final recipe pass runs at its HEAD, the report at its TAIL.
 * (The unread-recipe check that ran at HEAD of {@code apply} before 1.21.2 now runs in the file filter: tags load
 * before any reload listener since 1.21.2.)
 */
@Mixin(RecipeManager.class)
public abstract class RecipeManagerMixin {
    @Inject(method = "finalizeRecipeLoading", at = @At("HEAD"))
    private void scalpel$finalRecipePass(FeatureFlagSet features, CallbackInfo ci) {
        ServerData.finalRecipePass((RecipeManager) (Object) this);
    }

    @Inject(method = "finalizeRecipeLoading", at = @At("TAIL"))
    private void scalpel$afterDataLoad(FeatureFlagSet features, CallbackInfo ci) {
        ServerData.afterDataLoad();
    }
}
