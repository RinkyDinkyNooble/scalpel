package com.rinkynooble.scalpel.common.data;

import com.rinkynooble.scalpel.core.ScalpelCore;
import com.rinkynooble.scalpel.common.Scalpel;
import com.rinkynooble.scalpel.common.registry.RegistryCutter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Creative tabs, which mods fill in code: filtered once when they are built, leaving out cut and hidden items, and a
 * tab whose icon is cut shows its first remaining item instead. Villager and wandering trader trades are data since
 * 26.1: {@link DataFilter} filters their files, and {@link #filterOffer} is the last check on a rolled offer (hidden
 * items stay in trades). That check only runs when a villager rolls new trades.
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

    /**
     * The last check on a villager or wandering trader offer, at the end of {@code VillagerTrade#getOffer}. Trades are
     * data since 26.1, and {@link DataFilter} already left out every trade whose file names cut content; this catches
     * what a trade's item modifiers make while it rolls (a mod's function that turns the result into a cut item, or a
     * cut enchantment). Such an offer comes back as null, which the game treats as "no offer". {@code tradeId} gives
     * the trade's id for the report (null when it isn't registered); it is only asked for when an offer is refused.
     */
    @Nullable
    public static MerchantOffer filterOffer(@Nullable MerchantOffer offer, Supplier<String> tradeId) {
        if (offer == null || !Scalpel.core().applies()) {
            return offer;
        }
        for (ItemStack stack : new ItemStack[]{offer.getResult(), offer.getBaseCostA(), offer.getCostB()}) {
            String cut = CutEnchantments.firstCut(stack);
            if (cut == null && !stack.isEmpty() && RegistryCutter.isCutItem(stack.getItem())) {
                cut = RegistryCutter.itemId(stack.getItem());
            }
            if (cut != null) {
                String id = tradeId.get();
                String source = id == null ? "a villager trade" : id;
                if (reportedTrades.add(source + "|" + cut)) {
                    DataFilter.change(Scalpel.core(), "trade offer removed", cut, source + " (made while rolling the trade)");
                    Scalpel.core().writeReportLater();
                }
                return null;
            }
        }
        return offer;
    }
}
