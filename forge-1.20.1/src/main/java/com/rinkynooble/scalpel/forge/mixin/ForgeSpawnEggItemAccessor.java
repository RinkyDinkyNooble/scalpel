package com.rinkynooble.scalpel.forge.mixin;

import net.minecraft.world.entity.EntityType;
import net.minecraftforge.common.ForgeSpawnEggItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.function.Supplier;

@Mixin(value = ForgeSpawnEggItem.class, remap = false)
public interface ForgeSpawnEggItemAccessor {
    @Accessor("typeSupplier")
    Supplier<? extends EntityType<?>> scalpel$typeSupplier();
}
