package com.rinkynooble.scalpel.common.mixin;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The recipe map the final pass reads and replaces (1.21.2: recipes are a {@link RecipeMap}, no replaceRecipes). */
@Mixin(RecipeManager.class)
public interface RecipeManagerAccessor {
    @Accessor("recipes")
    RecipeMap scalpel$recipes();

    @Accessor("recipes")
    void scalpel$setRecipes(RecipeMap recipes);

    @Accessor("registries")
    HolderLookup.Provider scalpel$registries();
}
