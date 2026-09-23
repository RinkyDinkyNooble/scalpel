package com.rinkynooble.scalpel.forge.mixin;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.SpawnEggItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SpawnEggItem.class)
public interface SpawnEggItemAccessor {
    @Accessor("defaultType")
    EntityType<?> scalpel$defaultType();
}
