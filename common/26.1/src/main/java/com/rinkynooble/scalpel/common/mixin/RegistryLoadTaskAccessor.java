package com.rinkynooble.scalpel.common.mixin;

import net.minecraft.core.Registry;
import net.minecraft.resources.RegistryLoadTask;
import net.minecraft.resources.ResourceKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The registry a datapack registry load task fills (26.1 loads each registry as its own task). */
@Mixin(RegistryLoadTask.class)
public interface RegistryLoadTaskAccessor {
    @Invoker("registryKey")
    ResourceKey<? extends Registry<?>> scalpel$registryKey();
}
