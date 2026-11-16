package com.rinkynooble.scalpel.common.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.rinkynooble.scalpel.common.client.Placeholders;
import net.minecraft.client.resources.model.BlockStateModelLoader;
import net.minecraft.resources.ResourceLocation;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;
import java.util.Map;

/**
 * Placeholder blocks use only Scalpel's blockstate file; the original mod's file is left out (it would warn for every
 * variant the placeholder doesn't have). Since 1.21 the loaded blockstate files are handed to this loader's
 * constructor, so the map is filtered as the constructor stores it. The field is made non-final because the wrapped
 * store runs from a separate method.
 */
@Mixin(BlockStateModelLoader.class)
public abstract class BlockStateModelLoaderMixin {
    @Shadow
    @Final
    @Mutable
    private Map<ResourceLocation, List<BlockStateModelLoader.LoadedJson>> blockStateResources;

    @WrapOperation(method = "<init>", at = @At(value = "FIELD", opcode = Opcodes.PUTFIELD,
            target = "Lnet/minecraft/client/resources/model/BlockStateModelLoader;blockStateResources:Ljava/util/Map;"))
    private void scalpel$onlyPlaceholderBlockstates(BlockStateModelLoader loader, Map<ResourceLocation, List<BlockStateModelLoader.LoadedJson>> blockStates,
                                                     Operation<Void> original) {
        original.call(loader, Placeholders.filterBlockstates(blockStates, BlockStateModelLoader.LoadedJson::source));
    }
}
