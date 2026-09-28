package com.rinkynooble.scalpel.forge.compat;

import com.rinkynooble.scalpel.forge.Scalpel;
import com.rinkynooble.scalpel.forge.registry.RegistryCutter;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Hides cut items and items a hide rule matched from JEI. Only loaded when JEI is installed.
 * JEI builds its item list from the creative tabs, which {@code TabsAndTrades} has already filtered, so this usually
 * finds nothing left to do. It catches stacks that other plugins add outside the tabs. The stacks come from JEI's own
 * list, so every NBT variant it shows (each enchanted book, potion and so on) is hidden along with the plain item.
 */
@JeiPlugin
public class ScalpelJeiPlugin implements IModPlugin {
    private static final ResourceLocation UID = new ResourceLocation(Scalpel.MOD_ID, "hide_cut");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        IIngredientManager ingredients = runtime.getIngredientManager();
        List<ItemStack> hide = ingredients.getAllIngredients(VanillaTypes.ITEM_STACK).stream()
                .filter(stack -> !stack.isEmpty() && RegistryCutter.isUnlisted(stack.getItem()))
                .toList();
        if (!hide.isEmpty()) {
            ingredients.removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, hide);
            Scalpel.core().log().info("Hid " + hide.size() + " item stacks from JEI.");
        }
    }
}
