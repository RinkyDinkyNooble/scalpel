package com.rinkynooble.scalpel.fabric.mixin;

import com.rinkynooble.scalpel.fabric.LootTableId;
import net.minecraft.world.level.storage.loot.LootDataId;
import net.minecraft.world.level.storage.loot.LootDataManager;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Names a loot table when the game looks it up, so generated-loot changes in the report say which table made them.
 * At lookup rather than at load: Fabric API rebuilds tables after loading for its loot events.
 */
@Mixin(LootDataManager.class)
public abstract class LootDataManagerMixin {
    @Inject(method = "getElement", at = @At("RETURN"))
    private <T> void scalpel$nameTable(LootDataId<T> id, CallbackInfoReturnable<T> cir) {
        if (cir.getReturnValue() instanceof LootTable table && ((LootTableId) table).scalpel$id() == null) {
            ((LootTableId) table).scalpel$setId(id.location().toString());
        }
    }
}
