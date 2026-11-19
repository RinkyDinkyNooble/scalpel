package com.rinkynooble.scalpel.common.data;

import com.rinkynooble.scalpel.common.Scalpel;
import com.rinkynooble.scalpel.common.registry.RegistryCutter;
import com.rinkynooble.scalpel.core.ScalpelCore;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Takes cut items out of loot as it drops. The loot table filter works on item ids in the JSON, but loot functions can
 * turn one item into another while it drops (vanilla enchants {@code minecraft:book} into
 * {@code minecraft:enchanted_book}), so this catches the result. It also takes cut enchantments off whatever drops, and
 * an enchanted book left with none is dropped. Each port calls it at the end of loot generation (Forge: a global loot
 * modifier that runs last).
 */
public final class GeneratedLoot {
    /** Loot table and item pairs already in the report, so a drop is recorded once rather than every time. */
    private static final Set<String> reported = ConcurrentHashMap.newKeySet();

    private GeneratedLoot() {
    }

    static void reset() {
        reported.clear();
    }

    /** Removes cut stacks from {@code loot} (and cut enchantments from the rest). {@code table} names the source. */
    public static void strip(List<ItemStack> loot, String table) {
        ScalpelCore core = Scalpel.core();
        if (!core.applies()) {
            return;
        }
        loot.removeIf(stack -> isCutDrop(core, stack, table));
    }

    /**
     * One dropped stack, for ports that see loot one stack at a time (Fabric). Takes cut enchantments off it and returns
     * false when the stack itself must not drop.
     */
    public static boolean keep(ItemStack stack, String table) {
        ScalpelCore core = Scalpel.core();
        return !core.applies() || !isCutDrop(core, stack, table);
    }

    private static boolean isCutDrop(ScalpelCore core, ItemStack stack, String table) {
        if (stack.isEmpty()) {
            return false;
        }
        for (String enchantment : CutEnchantments.strip(stack)) {
            if (reported.add(table + "|" + enchantment)) {
                DataFilter.change(core, "generated loot enchantment removed", enchantment, "made by a loot function or modifier in " + table);
                core.writeReportLater();
            }
        }
        if (CutEnchantments.isEmptyBook(stack)) {
            return true;
        }
        if (!RegistryCutter.isCutItem(stack.getItem())) {
            return false;
        }
        String id = RegistryCutter.itemId(stack.getItem());
        if (reported.add(table + "|" + id)) {
            DataFilter.change(core, "generated loot removed", id, "made by a loot function or modifier in " + table);
            core.writeReportLater();
        }
        return true;
    }
}
