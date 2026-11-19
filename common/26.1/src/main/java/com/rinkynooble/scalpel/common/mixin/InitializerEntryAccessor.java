package com.rinkynooble.scalpel.common.mixin;

import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.resources.ResourceKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Which registry entry a pending component initializer is for (a private record of {@code DataComponentInitializers}). */
@Mixin(targets = "net.minecraft.core.component.DataComponentInitializers$InitializerEntry")
public interface InitializerEntryAccessor {
    @Accessor("key")
    ResourceKey<?> scalpel$key();

    /** The initializer itself: a spawn egg's entity is read from it at registration, before components are bound. */
    @Accessor("initializer")
    DataComponentInitializers.Initializer<?> scalpel$initializer();
}
