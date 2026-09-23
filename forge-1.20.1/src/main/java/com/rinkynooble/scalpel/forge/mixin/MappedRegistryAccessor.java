package com.rinkynooble.scalpel.forge.mixin;

import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(MappedRegistry.class)
public interface MappedRegistryAccessor<T> {
    /** Holders created by objects that have not been registered yet. Null once the registry is frozen. */
    @Accessor("unregisteredIntrusiveHolders")
    Map<T, Holder.Reference<T>> scalpel$unregisteredIntrusiveHolders();
}
