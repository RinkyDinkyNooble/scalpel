package com.rinkynooble.scalpel.common.mixin;

import com.google.gson.JsonElement;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.rinkynooble.scalpel.common.data.DataFilter;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Recipes, advancements, loot tables and loot modifiers are all listed here before anything parses them. Since 1.21.2
 * this method parses and decodes each file in one go, so the filter works on the file list instead: dropped files
 * leave the list, changed ones are handed back as new JSON.
 */
@Mixin(SimpleJsonResourceReloadListener.class)
public abstract class SimpleJsonResourceReloadListenerMixin {
    @WrapOperation(method = "scanDirectory(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/resources/FileToIdConverter;Lcom/mojang/serialization/DynamicOps;Lcom/mojang/serialization/Codec;Ljava/util/Map;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/resources/FileToIdConverter;listMatchingResources(Lnet/minecraft/server/packs/resources/ResourceManager;)Ljava/util/Map;"))
    private static Map<Identifier, Resource> scalpel$filter(FileToIdConverter lister, ResourceManager manager,
                                                            Operation<Map<Identifier, Resource>> original) {
        return DataFilter.filterFiles(lister, original.call(lister, manager));
    }

    /**
     * NeoForge 26.1 reads recipes through its own {@code scanDirectoryWithModifier}: every file's JSON is read into a
     * map, mods change that map ({@code ModifyRecipeJsonsEvent}), then it is decoded. The filter runs on the map right
     * after the mods' change, so what they added is filtered too. Optional: vanilla (Fabric) has no such method.
     */
    @WrapOperation(method = "scanDirectoryWithModifier",
            at = @At(value = "INVOKE", target = "Ljava/util/function/Consumer;accept(Ljava/lang/Object;)V"), require = 0)
    private static void scalpel$filterJson(Consumer<Map<Identifier, JsonElement>> modifier, Object jsons, Operation<Void> original,
                                           @Local(argsOnly = true) FileToIdConverter lister) {
        original.call(modifier, jsons);
        @SuppressWarnings("unchecked")
        Map<Identifier, JsonElement> files = (Map<Identifier, JsonElement>) jsons;
        DataFilter.afterScan(lister.prefix(), files);
    }
}
