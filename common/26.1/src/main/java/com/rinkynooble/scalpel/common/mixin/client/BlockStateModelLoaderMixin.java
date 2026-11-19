package com.rinkynooble.scalpel.common.mixin.client;

import com.llamalad7.mixinextras.sugar.Local;
import com.rinkynooble.scalpel.common.client.Placeholders;
import net.minecraft.client.resources.model.BlockStateModelLoader;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.List;

/**
 * Placeholder blocks use only Scalpel's blockstate file; the original mod's file is left out (it would load the mod's
 * models and warn for every variant the placeholder doesn't have). Since 1.21.4 the loader is static and asynchronous:
 * each block's blockstate files (one per pack, bottom first) reach {@code loadBlockStateDefinitionStack} as a list,
 * which is filtered here.
 */
@Mixin(BlockStateModelLoader.class)
public abstract class BlockStateModelLoaderMixin {
    @ModifyVariable(method = "loadBlockStateDefinitionStack", at = @At("HEAD"), argsOnly = true)
    private static List<?> scalpel$onlyPlaceholderBlockstates(List<?> stack, @Local(argsOnly = true) Identifier blockId) {
        return Placeholders.filterBlockstateStack(blockId, stack,
                definition -> ((LoadedBlockStateModelDispatcherAccessor) definition).scalpel$source());
    }
}
