package com.rinkynooble.scalpel.common.mixin;

import net.minecraft.resources.FileToIdConverter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The data folder a converter lists ({@code recipe}, {@code loot_table}...). */
@Mixin(FileToIdConverter.class)
public interface FileToIdConverterAccessor {
    @Accessor("prefix")
    String scalpel$prefix();
}
