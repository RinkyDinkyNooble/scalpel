package com.rinkynooble.scalpel.common.client;

import com.rinkynooble.scalpel.common.Scalpel;
import com.rinkynooble.scalpel.common.content.Redacted;
import com.rinkynooble.scalpel.common.content.RemovedItem;
import com.rinkynooble.scalpel.common.registry.RegistryCutter;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;
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
     * Keeps only Scalpel's blockstate file for a placeholder block. Otherwise the original mod's blockstate file loads
     * too, with its models, and logs a warning for every variant the placeholder does not have. {@code stack} holds
     * one loaded file per pack for the block {@code blockId} (1.21.4+).
     */
    public static <T> List<T> filterBlockstateStack(Identifier blockId, List<T> stack, Function<T, String> source) {
        PlaceholderPack placeholders = pack;
        if (placeholders == null || placeholders.isEmpty() || stack.size() < 2) {
            return stack;
        }
        Identifier file = Identifier.fromNamespaceAndPath(blockId.getNamespace(), "blockstates/" + blockId.getPath() + ".json");
        if (!placeholders.providesBlockstate(file)) {
            return stack;
        }
        List<T> ours = new ArrayList<>();
        for (T json : stack) {
            if (PlaceholderPack.ID.equals(source.apply(json))) {
                ours.add(json);
            }
        }
        return ours.isEmpty() ? stack : ours;
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
