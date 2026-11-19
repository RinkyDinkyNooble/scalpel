package com.rinkynooble.scalpel.common.mixin;

import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Collection;

/**
 * The recipe map the final pass reads and replaces. 26.3 builds the manager from the recipe registry, so both fields
 * are final and set in the constructor: the learnable list (what {@code /recipe give *} hands out) is built from the
 * map there and is replaced with it.
 */
@Mixin(RecipeManager.class)
public interface RecipeManagerAccessor {
    @Accessor("recipes")
    RecipeMap scalpel$recipes();

    @Mutable
    @Accessor("recipes")
    void scalpel$setRecipes(RecipeMap recipes);

    @Mutable
    @Accessor("learnableRecipes")
    void scalpel$setLearnableRecipes(Collection<RecipeHolder<?>> recipes);
}
