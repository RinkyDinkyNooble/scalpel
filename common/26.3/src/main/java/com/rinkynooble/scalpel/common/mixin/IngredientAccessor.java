package com.rinkynooble.scalpel.common.mixin;

import net.minecraft.core.HolderSet;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * An ingredient's items or tag. Vanilla 26.1 keeps them in the private field {@code values} with no getter;
 * {@code getValues()} is NeoForge's addition (Forge makes the field protected, Fabric leaves it private), so the shared
 * code reads the field, which all three loaders have under that name.
 */
@Mixin(Ingredient.class)
public interface IngredientAccessor {
    @Accessor("values")
    HolderSet<Item> scalpel$values();
}
