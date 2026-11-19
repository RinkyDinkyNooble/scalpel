package com.rinkynooble.scalpel.common.mixin;

import com.google.gson.JsonElement;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.rinkynooble.scalpel.common.data.CutEnchantments;
import com.rinkynooble.scalpel.common.data.WorldgenFilter;
import net.minecraft.resources.ResourceKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.io.Reader;

/**
 * Each datapack registry file's JSON goes through the filters before it is decoded (worldgen, enchantments, mods' own
 * registries). 26.1 reads every file in {@code RegistryLoadTask.PendingRegistration#loadFromResource}, which also runs
 * on a client that reads a server's entries from its own copy of a pack.
 */
@Mixin(targets = "net.minecraft.resources.RegistryLoadTask$PendingRegistration")
public abstract class PendingRegistrationMixin {
    @WrapOperation(method = "loadFromResource",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/StrictJsonParser;parse(Ljava/io/Reader;)Lcom/google/gson/JsonElement;"))
    private static JsonElement scalpel$filter(Reader reader, Operation<JsonElement> original,
                                              @Local(argsOnly = true) ResourceKey<?> element) {
        JsonElement json = original.call(reader);
        if (CutEnchantments.handles(element.registry())) {
            return CutEnchantments.filter(element.registry(), element.identifier(), json);
        }
        return WorldgenFilter.filter(element.registry(), element.identifier(), json);
    }
}
