package com.rinkynooble.scalpel.neoforge.mixin;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Mixin(value = DeferredSpawnEggItem.class, remap = false)
public interface DeferredSpawnEggItemAccessor {
    @Accessor("typeSupplier")
    Supplier<? extends EntityType<?>> scalpel$typeSupplier();

    /** Every NeoForge egg, filled by the constructor. Common setup and the client's color handler loop over it. */
    @Accessor("MOD_EGGS")
    static List<DeferredSpawnEggItem> scalpel$modEggs() {
        throw new AssertionError();
    }

    @Accessor("TYPE_MAP")
    static Map<EntityType<? extends Mob>, DeferredSpawnEggItem> scalpel$typeMap() {
        throw new AssertionError();
    }
}
