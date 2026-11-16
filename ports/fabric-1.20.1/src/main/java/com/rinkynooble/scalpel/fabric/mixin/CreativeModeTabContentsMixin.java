package com.rinkynooble.scalpel.fabric.mixin;

import com.rinkynooble.scalpel.common.Scalpel;
import com.rinkynooble.scalpel.common.data.TabsAndTrades;
import com.rinkynooble.scalpel.common.registry.RegistryCutter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;
import java.util.Set;

/** Creative tabs on Fabric: cut and hidden items leave a tab once it is built (Fabric API's tab events run inside). */
@Mixin(CreativeModeTab.class)
public abstract class CreativeModeTabContentsMixin {
    @Shadow
    private Collection<ItemStack> displayItems;

    @Shadow
    private Set<ItemStack> displayItemsSearchTab;

    @Shadow
    public abstract void rebuildSearchTree();

    @Inject(method = "buildContents", at = @At("TAIL"))
    private void scalpel$leaveOutCut(CreativeModeTab.ItemDisplayParameters parameters, CallbackInfo ci) {
        if (!Scalpel.core().applies()) {
            return;
        }
        int before = displayItems.size();
        boolean changed = displayItems.removeIf(RegistryCutter::isUnlisted);
        changed |= displayItemsSearchTab.removeIf(RegistryCutter::isUnlisted);
        if (changed) {
            rebuildSearchTree();
            TabsAndTrades.reportTabEntries(String.valueOf(BuiltInRegistries.CREATIVE_MODE_TAB.getKey((CreativeModeTab) (Object) this)),
                    before - displayItems.size());
        }
    }
}
