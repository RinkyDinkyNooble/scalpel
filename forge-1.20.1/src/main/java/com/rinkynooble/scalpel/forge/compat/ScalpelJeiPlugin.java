package com.rinkynooble.scalpel.forge.compat;

import com.rinkynooble.scalpel.forge.Scalpel;
import com.rinkynooble.scalpel.forge.registry.RegistryCutter;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Hides redacted, hidden and removed items from JEI. Only loaded when JEI is installed. */
@JeiPlugin
public class ScalpelJeiPlugin implements IModPlugin {
    private static final ResourceLocation UID = new ResourceLocation(Scalpel.MOD_ID, "hide_cut");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        List<ItemStack> hide = BuiltInRegistries.ITEM.stream()
                .filter(RegistryCutter::isCutItem)
                .map(ItemStack::new)
                .toList();
        if (!hide.isEmpty()) {
            runtime.getIngredientManager().removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, hide);
            Scalpel.core().log().info("Hid " + hide.size() + " items from JEI.");
        }
    }
}
