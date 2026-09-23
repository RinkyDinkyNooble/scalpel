package com.rinkynooble.scalpel.forge.mixin;

import com.google.gson.JsonElement;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.rinkynooble.scalpel.forge.data.WorldgenFilter;
import net.minecraft.core.Registry;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.ForgeHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.io.Reader;

/** Worldgen and other datapack registries: each file's JSON goes through the worldgen filter before it is decoded. */
@Mixin(RegistryDataLoader.class)
public abstract class RegistryDataLoaderMixin {
    @WrapOperation(method = "loadRegistryContents",
            at = @At(value = "INVOKE", target = "Lcom/google/gson/JsonParser;parseReader(Ljava/io/Reader;)Lcom/google/gson/JsonElement;", remap = false))
    private static JsonElement scalpel$filter(Reader reader, Operation<JsonElement> original,
                                              @Local(argsOnly = true) ResourceKey<? extends Registry<?>> registryKey,
                                              @Local ResourceLocation file) {
        JsonElement json = original.call(reader);
        return WorldgenFilter.filter(ForgeHooks.prefixNamespace(registryKey.location()), file, json);
    }
}
