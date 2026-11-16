package com.rinkynooble.scalpel.common.client;

import com.rinkynooble.scalpel.common.Scalpel;
import com.rinkynooble.scalpel.common.content.Redacted;
import com.rinkynooble.scalpel.common.content.RemovedItem;
import com.rinkynooble.scalpel.common.registry.RegistryCutter;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Client pieces every port shares: the placeholder resource pack, the blockstate filter and the tooltip line. */
public final class Placeholders {
    private static PlaceholderPack pack;

    private Placeholders() {
    }

    /** The placeholder pack, built the first time resources load (registration is over by then). */
    public static synchronized PlaceholderPack pack() {
        if (pack == null) {
            pack = new PlaceholderPack();
        }
        return pack;
    }

    /**
     * Keeps only Scalpel's blockstate file for placeholder blocks. Otherwise the original mod's blockstate file loads
     * too and logs a warning for every variant the one-state placeholder does not have.
     */
    public static <T> Map<ResourceLocation, List<T>> filterBlockstates(Map<ResourceLocation, List<T>> stacks, Function<T, String> source) {
        PlaceholderPack placeholders = pack;
        if (placeholders == null || placeholders.isEmpty()) {
            return stacks;
        }
        Map<ResourceLocation, List<T>> filtered = null;
        for (Map.Entry<ResourceLocation, List<T>> entry : stacks.entrySet()) {
            if (!placeholders.providesBlockstate(entry.getKey()) || entry.getValue().size() < 2) {
                continue;
            }
            if (filtered == null) {
                filtered = new HashMap<>(stacks);
            }
            List<T> ours = new ArrayList<>();
            for (T json : entry.getValue()) {
                if (PlaceholderPack.ID.equals(source.apply(json))) {
                    ours.add(json);
                }
            }
            filtered.put(entry.getKey(), ours);
        }
        return filtered == null ? stacks : filtered;
    }

    /** The tooltip line for a removed or redacted vanilla item, or null (placeholders name themselves). */
    public static Component tooltipLine(Item item) {
        if (item instanceof Redacted || !Scalpel.core().applies()) {
            return null;
        }
        if (RemovedItem.ID.equals(RegistryCutter.itemId(item))) {
            return Component.translatable("tooltip.scalpel.removed").withStyle(ChatFormatting.GRAY);
        }
        if (RegistryCutter.isCutItem(item)) {
            return Component.translatable("tooltip.scalpel.redacted_vanilla").withStyle(ChatFormatting.GRAY);
        }
        return null;
    }
}
