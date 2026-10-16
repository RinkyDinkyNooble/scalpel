package com.rinkynooble.scalpel.forge.data;

import com.rinkynooble.scalpel.core.ScalpelCore;
import com.rinkynooble.scalpel.forge.Scalpel;
import com.rinkynooble.scalpel.forge.registry.RegistryCutter;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.village.VillagerTradesEvent;
import net.minecraftforge.event.village.WandererTradesEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
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

    public static void register(IEventBus modBus, IEventBus forgeBus) {
        modBus.addListener(EventPriority.LOWEST, TabsAndTrades::onBuildTab);
        forgeBus.addListener(EventPriority.LOWEST, TabsAndTrades::onVillagerTrades);
        forgeBus.addListener(EventPriority.LOWEST, TabsAndTrades::onWandererTrades);
    }

    private static void onBuildTab(BuildCreativeModeTabContentsEvent event) {
        ScalpelCore core = Scalpel.core();
        if (!core.applies()) {
            return;
        }
        List<ItemStack> remove = new ArrayList<>();
        for (var entry : event.getEntries()) {
            if (RegistryCutter.isUnlisted(entry.getKey().getItem())) {
                remove.add(entry.getKey());
            }
        }
        if (remove.isEmpty()) {
            return;
        }
        for (ItemStack stack : remove) {
            event.getEntries().remove(stack);
        }
        DataFilter.change(core, "creative tab entries removed", event.getTabKey().location().toString(), String.valueOf(remove.size()));
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

    private static void onVillagerTrades(VillagerTradesEvent event) {
        String source = "villager " + event.getType();
        Int2ObjectMap<List<VillagerTrades.ItemListing>> trades = event.getTrades();
        for (Int2ObjectMap.Entry<List<VillagerTrades.ItemListing>> level : trades.int2ObjectEntrySet()) {
            wrapAll(level.getValue(), source + " level " + level.getIntKey());
        }
    }

    private static void onWandererTrades(WandererTradesEvent event) {
        wrapAll(event.getGenericTrades(), "wandering trader");
        wrapAll(event.getRareTrades(), "wandering trader (rare)");
    }

    private static void wrapAll(List<VillagerTrades.ItemListing> listings, String source) {
        listings.replaceAll(listing -> listing instanceof Filtered ? listing : new Filtered(listing, source));
    }

    /** An offer that contains cut content comes back as null, which the game treats as "no offer". */
    private record Filtered(VillagerTrades.ItemListing inner, String source) implements VillagerTrades.ItemListing {
        @Nullable
        @Override
        public MerchantOffer getOffer(Entity trader, RandomSource random) {
            MerchantOffer offer = inner.getOffer(trader, random);
            if (offer == null || !Scalpel.core().applies()) {
                return offer;
            }
            for (ItemStack stack : new ItemStack[]{offer.getResult(), offer.getBaseCostA(), offer.getCostB()}) {
                if (!stack.isEmpty() && RegistryCutter.isCutItem(stack.getItem())) {
                    String id = RegistryCutter.itemId(stack.getItem());
                    if (reportedTrades.add(source + "|" + id)) {
                        DataFilter.change(Scalpel.core(), "trade offer removed", id, source + " (" + inner.getClass().getName() + ")");
                        Scalpel.core().writeReport();
                    }
                    return null;
                }
            }
            return offer;
        }
    }
}
