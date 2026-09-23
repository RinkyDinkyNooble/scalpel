package com.rinkynooble.scalpel.forge.mixin;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.rinkynooble.scalpel.forge.data.DataFilter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/** Recipes, advancements, loot tables and loot modifiers are all read through here, before anything parses them. */
@Mixin(SimpleJsonResourceReloadListener.class)
public abstract class SimpleJsonResourceReloadListenerMixin {
    @Inject(method = "scanDirectory", at = @At("TAIL"))
    private static void scalpel$filter(ResourceManager manager, String directory, Gson gson,
                                       Map<ResourceLocation, JsonElement> output, CallbackInfo ci) {
        DataFilter.afterScan(directory, output);
    }
}
