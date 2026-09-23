package com.rinkynooble.scalpel.forge.content;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Stands in for a redacted item. No creative tab, no behaviour. */
public class RedactedItem extends Item implements Redacted {
    private final ResourceLocation originalId;

    public RedactedItem(ResourceLocation originalId) {
        super(new Item.Properties());
        this.originalId = originalId;
    }

    @Override
    public ResourceLocation originalId() {
        return originalId;
    }

    @Override
    public String getDescriptionId() {
        return "item.scalpel.redacted";
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.scalpel.original", originalId.toString()).withStyle(ChatFormatting.GRAY));
    }
}
