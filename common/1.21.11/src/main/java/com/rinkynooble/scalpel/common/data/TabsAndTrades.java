package com.rinkynooble.scalpel.common.data;

import com.rinkynooble.scalpel.core.ScalpelCore;
import com.rinkynooble.scalpel.common.Scalpel;
import com.rinkynooble.scalpel.common.registry.RegistryCutter;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.VillagerTrades;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Two things mods fill in code rather than data: creative tabs and villager / wandering trader trades.
 * Tabs are filtered once when they are built, leaving out cut and hidden items, and a tab whose icon is cut shows its
 * first remaining item instead. Trades keep hidden items, and are
 * wrapped so an offer that would contain cut content
 * is skipped (the game already skips offers that come back empty); that check only runs when a villager
 * rolls new trades.
 */
public final class TabsAndTrades {
    private static final Set<String> reportedTrades = ConcurrentHashMap.newKeySet();

    private TabsAndTrades() {
    }

    /** Reports how many entries a tab lost (the port takes them out of the tab its own way). */
    public static void reportTabEntries(String tabId, int removed) {
        if (removed > 0) {
            DataFilter.change(Scalpel.core(), "creative tab entries removed", tabId, String.valueOf(removed));
        }
    }

    /**
     * A tab's icon when its own is cut. Many mods draw their tab with one of their own items, and a cut one leaves the
     * tab button blank, so the first item the tab still shows takes its place. Null keeps the icon.
     */
    @Nullable
    public static ItemStack iconFor(CreativeModeTab tab, ItemStack icon, Collection<ItemStack> shown) {
        ScalpelCore core = Scalpel.core();
        if (!core.applies() || (!icon.isEmpty() && !RegistryCutter.isCutItem(icon.getItem()))) {
            return null;
        }
        for (ItemStack stack : shown) {
            if (!stack.isEmpty() && !RegistryCutter.isUnlisted(stack.getItem())) {
                String was = icon.isEmpty() ? "no icon" : RegistryCutter.itemId(icon.getItem()) + " is cut";
                DataFilter.change(core, "creative tab icon replaced", String.valueOf(BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab)),
                        was + ", now " + RegistryCutter.itemId(stack.getItem()));
                return stack.copyWithCount(1);
            }
        }
        return null;
    }

    /** Wraps every listing so an offer with cut content is skipped. {@code listings} must be mutable. */
    public static void wrapAll(List<VillagerTrades.ItemListing> listings, String source) {
        listings.replaceAll(listing -> listing instanceof Filtered ? listing : new Filtered(listing, source));
    }

    /** An offer that contains cut content comes back as null, which the game treats as "no offer". */
    private record Filtered(VillagerTrades.ItemListing inner, String source) implements VillagerTrades.ItemListing {
        @Nullable
        @Override
        public MerchantOffer getOffer(ServerLevel level, Entity trader, RandomSource random) {
            MerchantOffer offer = inner.getOffer(level, trader, random);
            if (offer == null || !Scalpel.core().applies()) {
                return offer;
            }
            for (ItemStack stack : new ItemStack[]{offer.getResult(), offer.getBaseCostA(), offer.getCostB()}) {
                String enchantment = CutEnchantments.firstCut(stack);
                if (enchantment != null) {
                    if (reportedTrades.add(source + "|" + enchantment)) {
                        DataFilter.change(Scalpel.core(), "trade offer removed", enchantment, source + " (" + inner.getClass().getName() + ")");
                        Scalpel.core().writeReportLater();
                    }
                    return null;
                }
                if (!stack.isEmpty() && RegistryCutter.isCutItem(stack.getItem())) {
                    String id = RegistryCutter.itemId(stack.getItem());
                    if (reportedTrades.add(source + "|" + id)) {
                        DataFilter.change(Scalpel.core(), "trade offer removed", id, source + " (" + inner.getClass().getName() + ")");
                        Scalpel.core().writeReportLater();
                    }
                    return null;
                }
            }
            return offer;
        }
    }
}
