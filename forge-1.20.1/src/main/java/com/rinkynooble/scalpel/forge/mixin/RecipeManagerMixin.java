package com.rinkynooble.scalpel.forge.mixin;

import com.google.gson.JsonElement;
import com.rinkynooble.scalpel.forge.data.DataFilter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/**
 * Recipe files once tags are known, before they are parsed. KubeJS takes over recipe loading at the same HEAD
 * (priority 1100): it posts its recipe event with this map and cancels. HEAD callbacks run in priority order, lowest
 * first, so this one runs before KubeJS and KubeJS reads the filtered files (harness case 30).
 */
@Mixin(value = RecipeManager.class, priority = 900)
public abstract class RecipeManagerMixin {
    @Inject(method = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
            at = @At("HEAD"))
    private void scalpel$beforeApply(Map<ResourceLocation, JsonElement> recipes, ResourceManager manager, ProfilerFiller profiler, CallbackInfo ci) {
        DataFilter.beforeRecipeApply(recipes);
    }
}
