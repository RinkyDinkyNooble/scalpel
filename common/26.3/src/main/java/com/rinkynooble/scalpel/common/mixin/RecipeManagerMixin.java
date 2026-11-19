package com.rinkynooble.scalpel.common.mixin;

import com.rinkynooble.scalpel.common.data.ServerData;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The end of a server data load. {@code finalizeRecipeLoading} runs on the main thread once the data is loaded and
 * tags are bound (startup and /reload), and builds the stonecutter list, recipe book and what clients receive from the
 * recipe map. The final recipe pass runs at its HEAD, the report at its TAIL. (26.3: recipes are a datapack registry,
 * so the file pass runs in {@code RegistryFiles}; the manager is built from that registry.)
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
