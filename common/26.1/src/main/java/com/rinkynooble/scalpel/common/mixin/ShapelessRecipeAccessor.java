package com.rinkynooble.scalpel.common.mixin;

import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(ShapelessRecipe.class)
public interface ShapelessRecipeAccessor {
    @Accessor("result")
    ItemStackTemplate scalpel$result();

    @Accessor("ingredients")
    List<Ingredient> scalpel$ingredients();
}
