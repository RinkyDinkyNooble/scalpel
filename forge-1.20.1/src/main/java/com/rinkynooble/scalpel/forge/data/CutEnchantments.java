package com.rinkynooble.scalpel.forge.data;

import com.rinkynooble.scalpel.core.ContentType;
import com.rinkynooble.scalpel.core.Resolver;
import com.rinkynooble.scalpel.core.ScalpelCore;
import com.rinkynooble.scalpel.forge.Scalpel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enchantments are never swapped for a placeholder: every one is its mod's own class, and mods read theirs directly.
 * A redacted enchantment stays registered as itself and goes inert instead: {@code EnchantmentHelperMixin} makes the
 * game read level 0 and leaves it out of what an item says it has, which every effect goes through. The checks here
 * keep new ones from being handed out in loot, trades, creative tabs and recipe viewers.
 */
public final class CutEnchantments {
    private static final String[] NBT_LISTS = {ItemStack.TAG_ENCH, EnchantedBookItem.TAG_STORED_ENCHANTMENTS};
    /** Filled while registries load and read on every enchantment lookup, so it stays empty unless a rule needs it. */
    private static final Set<Enchantment> inert = ConcurrentHashMap.newKeySet();

    private CutEnchantments() {
    }

    public static void markInert(Enchantment enchantment) {
        inert.add(enchantment);
    }

    public static boolean isInert(Enchantment enchantment) {
        return !inert.isEmpty() && inert.contains(enchantment);
    }

    private static boolean anyCut(Resolver resolver) {
        return !resolver.cutIds(ContentType.ENCHANTMENT).isEmpty();
    }

    /** The first cut enchantment on a stack, or null. */
    static String firstCut(ItemStack stack) {
        ScalpelCore core = Scalpel.core();
        if (!core.applies() || !anyCut(core.resolver()) || !stack.hasTag()) {
            return null;
        }
        for (String key : NBT_LISTS) {
            ListTag list = stack.getTag().getList(key, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                String id = id(list.getCompound(i));
                if (core.resolver().isCut(ContentType.ENCHANTMENT, id)) {
                    return id;
                }
            }
        }
        return null;
    }

    /** Takes cut enchantments off a stack and returns their ids. */
    static List<String> strip(ItemStack stack) {
        List<String> stripped = new ArrayList<>();
        if (firstCut(stack) == null) {
            return stripped;
        }
        Resolver resolver = Scalpel.core().resolver();
        CompoundTag tag = stack.getTag();
        for (String key : NBT_LISTS) {
            if (!tag.contains(key, Tag.TAG_LIST)) {
                continue;
            }
            ListTag list = tag.getList(key, Tag.TAG_COMPOUND);
            list.removeIf(entry -> {
                String id = id((CompoundTag) entry);
                if (resolver.isCut(ContentType.ENCHANTMENT, id)) {
                    stripped.add(id);
                    return true;
                }
                return false;
            });
            if (list.isEmpty()) {
                tag.remove(key);
            }
        }
        return stripped;
    }

    /** An enchanted book with nothing on it, which {@link #strip} can leave behind. */
    static boolean isEmptyBook(ItemStack stack) {
        return anyCut(Scalpel.core().resolver()) && stack.is(Items.ENCHANTED_BOOK) && EnchantedBookItem.getEnchantments(stack).isEmpty();
    }

    /** True for an enchanted book whose enchantments are all cut or hidden, and not in a dry run. */
    public static boolean isUnlistedBook(ItemStack stack) {
        ScalpelCore core = Scalpel.core();
        Resolver resolver = core.resolver();
        if (!core.applies() || !stack.is(Items.ENCHANTED_BOOK)
                || (!anyCut(resolver) && resolver.hiddenEntries(ContentType.ENCHANTMENT).isEmpty())) {
            return false;
        }
        ListTag list = EnchantedBookItem.getEnchantments(stack);
        for (int i = 0; i < list.size(); i++) {
            String id = id(list.getCompound(i));
            if (!resolver.isCut(ContentType.ENCHANTMENT, id) && !resolver.isHidden(ContentType.ENCHANTMENT, id)) {
                return false;
            }
        }
        return !list.isEmpty();
    }

    private static String id(CompoundTag entry) {
        return String.valueOf(EnchantmentHelper.getEnchantmentId(entry));
    }
}
