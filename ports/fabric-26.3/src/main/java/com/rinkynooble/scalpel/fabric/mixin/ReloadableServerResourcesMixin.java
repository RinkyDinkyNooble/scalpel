package com.rinkynooble.scalpel.fabric.mixin;

import com.rinkynooble.scalpel.fabric.LootTableId;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.ReloadableServerRegistries;
import net.minecraft.server.ReloadableServerResources;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Loot tables are a registry reloaded with the data. Once a load has finished (every reload listener done, before the
 * recipes are finalized), each table gets the id it was loaded under, for the generated-loot report; nested tables
 * never pass through a lookup by id, so this names them too. The data load's start, final recipe pass and end are
 * vanilla hooks in the shared code (tags load before every listener since 1.21.2).
 */
@Mixin(ReloadableServerResources.class)
public abstract class ReloadableServerResourcesMixin {
    @Shadow
    public abstract ReloadableServerRegistries.Holder fullRegistries();

    @Inject(method = "updateComponentsAndStaticRegistryTags", at = @At("HEAD"))
    private void scalpel$nameLootTables(CallbackInfo ci) {
        fullRegistries().lookup().lookup(Registries.LOOT_TABLE).ifPresent(tables -> tables.listElements().forEach(holder -> {
            LootTableId table = (LootTableId) holder.value();
            if (table.scalpel$id() == null) {
                table.scalpel$setId(holder.key().identifier().toString());
            }
        }));
    }
}
