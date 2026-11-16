package com.rinkynooble.scalpel.common.mixin;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.SpawnEggItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/**
 * Since 1.21.5 a spawn egg names its entity in its {@code ENTITY_DATA} component (read in RegistryCutter), and the
 * constructor files the egg in this map by that entity.
 */
@Mixin(SpawnEggItem.class)
public interface SpawnEggItemAccessor {
    @Accessor("BY_ID")
    static Map<EntityType<?>, SpawnEggItem> scalpel$byId() {
        throw new AssertionError();
    }
}
