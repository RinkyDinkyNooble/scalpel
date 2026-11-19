package com.rinkynooble.scalpel.common.mixin;

import net.minecraft.core.component.DataComponentInitializers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/** The pending default components of every item built so far (26.1: items get their components after registries load). */
@Mixin(DataComponentInitializers.class)
public interface DataComponentInitializersAccessor {
    @Accessor("initializers")
    List<Object> scalpel$initializers();
}
