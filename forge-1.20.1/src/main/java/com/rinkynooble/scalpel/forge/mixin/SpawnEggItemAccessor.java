package com.rinkynooble.scalpel.forge.mixin;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.SpawnEggItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(SpawnEggItem.class)
public interface SpawnEggItemAccessor {
    @Accessor("defaultType")
    EntityType<?> scalpel$defaultType();

    /** Every vanilla-style egg by its entity, filled by the constructor. Item colors are registered from it. */
    @Accessor("BY_ID")
    static Map<EntityType<? extends Mob>, SpawnEggItem> scalpel$byId() {
        throw new AssertionError();
    }
}
