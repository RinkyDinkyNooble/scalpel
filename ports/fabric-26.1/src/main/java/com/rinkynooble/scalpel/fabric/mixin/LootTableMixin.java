package com.rinkynooble.scalpel.fabric.mixin;

import com.rinkynooble.scalpel.common.data.GeneratedLoot;
import com.rinkynooble.scalpel.fabric.LootTableId;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.function.Consumer;

/**
 * Generated loot on Fabric, which has no global loot modifiers: every drop of every table passes through here (chests,
 * blocks, entities and fishing all end in this method), so cut items that loot functions create are caught.
 */
@Mixin(LootTable.class)
public abstract class LootTableMixin implements LootTableId {
    @Unique
    private String scalpel$id;

    @Override
    public String scalpel$id() {
        return scalpel$id;
    }

    @Override
    public void scalpel$setId(String id) {
        scalpel$id = id;
    }

    @ModifyVariable(method = "getRandomItemsRaw(Lnet/minecraft/world/level/storage/loot/LootContext;Ljava/util/function/Consumer;)V",
            at = @At("HEAD"), argsOnly = true)
    private Consumer<ItemStack> scalpel$withoutCut(Consumer<ItemStack> output) {
        String table = scalpel$id == null ? "a loot table" : scalpel$id;
        return stack -> {
            if (GeneratedLoot.keep(stack, table)) {
                output.accept(stack);
            }
        };
    }
}
