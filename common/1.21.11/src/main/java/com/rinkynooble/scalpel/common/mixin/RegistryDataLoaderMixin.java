package com.rinkynooble.scalpel.common.mixin;

import com.google.gson.JsonElement;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.rinkynooble.scalpel.common.data.CutEnchantments;
import com.rinkynooble.scalpel.common.data.RegistryFiles;
import com.rinkynooble.scalpel.common.data.WorldgenFilter;
import net.minecraft.core.WritableRegistry;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.io.Reader;
import java.util.Map;

/**
 * Datapack registries (worldgen, enchantments since 1.21, mods' own): the list of files is seen before any is read,
 * so removed enchantments are never loaded, and each file's JSON goes through the filters before it is decoded.
 * {@code loadElementFromResource} also runs on a client that reads a server's entries from its own copy of a pack.
 */
@Mixin(RegistryDataLoader.class)
public abstract class RegistryDataLoaderMixin {
    @WrapOperation(method = "loadContentsFromManager",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/resources/FileToIdConverter;listMatchingResources(Lnet/minecraft/server/packs/resources/ResourceManager;)Ljava/util/Map;"))
    private static Map<Identifier, Resource> scalpel$listFiles(FileToIdConverter converter, ResourceManager manager,
                                                                     Operation<Map<Identifier, Resource>> original,
                                                                     @Local(argsOnly = true) WritableRegistry<?> registry) {
        Map<Identifier, Resource> files = original.call(converter, manager);
        return RegistryFiles.beforeLoad(registry.key(), converter, files);
    }

    @WrapOperation(method = "loadElementFromResource",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/StrictJsonParser;parse(Ljava/io/Reader;)Lcom/google/gson/JsonElement;"))
    private static JsonElement scalpel$filter(Reader reader, Operation<JsonElement> original,
                                              @Local(argsOnly = true) ResourceKey<?> element) {
        // 1.21.11 parses with its own StrictJsonParser instead of Gson's JsonParser.parseReader.
        JsonElement json = original.call(reader);
        if (CutEnchantments.handles(element.registry())) {
            return CutEnchantments.filter(element.registry(), element.identifier(), json);
        }
        return WorldgenFilter.filter(element.registry(), element.identifier(), json);
    }
}
