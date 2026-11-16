package com.rinkynooble.scalpel.common.mixin;

import com.google.gson.JsonElement;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.rinkynooble.scalpel.common.data.RegistryFiles;
import com.rinkynooble.scalpel.common.data.WorldgenFilter;
import net.minecraft.core.Registry;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.io.Reader;
import java.util.Map;

/**
 * Worldgen and other datapack registries: the list of files is seen before any is read, so entries made from a removed
 * item are left out (trim materials and patterns), and each file's JSON goes through the worldgen filter before it is
 * decoded.
 */
@Mixin(RegistryDataLoader.class)
public abstract class RegistryDataLoaderMixin {
    @WrapOperation(method = "loadRegistryContents",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/resources/FileToIdConverter;listMatchingResources(Lnet/minecraft/server/packs/resources/ResourceManager;)Ljava/util/Map;"))
    private static Map<ResourceLocation, Resource> scalpel$listFiles(FileToIdConverter converter, ResourceManager manager,
                                                                     Operation<Map<ResourceLocation, Resource>> original,
                                                                     @Local(argsOnly = true) ResourceKey<? extends Registry<?>> registryKey) {
        Map<ResourceLocation, Resource> files = original.call(converter, manager);
        return RegistryFiles.beforeLoad(registryKey, converter, files);
    }

    @WrapOperation(method = "loadRegistryContents",
            at = @At(value = "INVOKE", target = "Lcom/google/gson/JsonParser;parseReader(Ljava/io/Reader;)Lcom/google/gson/JsonElement;", remap = false))
    private static JsonElement scalpel$filter(Reader reader, Operation<JsonElement> original,
                                              @Local(argsOnly = true) ResourceKey<? extends Registry<?>> registryKey,
                                              @Local ResourceLocation file) {
        JsonElement json = original.call(reader);
        return WorldgenFilter.filter(registryKey.location(), file, json);
    }
}
