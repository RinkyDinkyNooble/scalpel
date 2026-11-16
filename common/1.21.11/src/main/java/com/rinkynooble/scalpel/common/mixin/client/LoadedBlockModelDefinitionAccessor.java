package com.rinkynooble.scalpel.common.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Which pack a loaded blockstate file came from (a private record of {@code BlockStateModelLoader}). */
@Mixin(targets = "net.minecraft.client.resources.model.BlockStateModelLoader$LoadedBlockModelDefinition")
public interface LoadedBlockModelDefinitionAccessor {
    @Accessor("source")
    String scalpel$source();
}
