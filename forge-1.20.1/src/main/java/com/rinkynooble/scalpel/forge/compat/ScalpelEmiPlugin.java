package com.rinkynooble.scalpel.forge.compat;

import com.rinkynooble.scalpel.forge.registry.RegistryCutter;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import net.minecraft.world.item.ItemStack;

/** Hides cut items and items a hide rule matched from EMI. Only loaded when EMI is installed. */
@EmiEntrypoint
public class ScalpelEmiPlugin implements EmiPlugin {
    @Override
    public void register(EmiRegistry registry) {
        registry.removeEmiStacks(stack -> {
            ItemStack item = stack.getItemStack();
            return !item.isEmpty() && RegistryCutter.isUnlisted(item.getItem());
        });
    }
}
