package com.rinkynooble.scalpel.forge.mixin;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.common.ForgeSpawnEggItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Mixin(value = ForgeSpawnEggItem.class, remap = false)
public interface ForgeSpawnEggItemAccessor {
    @Accessor("typeSupplier")
    Supplier<? extends EntityType<?>> scalpel$typeSupplier();

    /** Every Forge egg, filled by the constructor. Common setup and the client's color handler loop over it. */
    @Accessor("MOD_EGGS")
    static List<ForgeSpawnEggItem> scalpel$modEggs() {
        throw new AssertionError();
    }

    @Accessor("TYPE_MAP")
    static Map<EntityType<? extends Mob>, ForgeSpawnEggItem> scalpel$typeMap() {
        throw new AssertionError();
    }
}
