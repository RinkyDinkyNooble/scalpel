package com.rinkynooble.scalpel.forge.content;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** The item for a redacted block. Places the {@link RedactedBlock}, nothing else. */
public class RedactedBlockItem extends BlockItem implements Redacted {
    private final ResourceLocation originalId;

    public RedactedBlockItem(RedactedBlock block, ResourceLocation originalId) {
        super(block, new Item.Properties());
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
