package com.rinkynooble.scalpel.forge.content;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Stands in for a redacted block: one state, one shared model, no drops, no behaviour. */
public class RedactedBlock extends Block implements Redacted {
    private final ResourceLocation originalId;

    public RedactedBlock(ResourceLocation originalId) {
        super(BlockBehaviour.Properties.of().instabreak().noLootTable().sound(SoundType.STONE));
        this.originalId = originalId;
    }

    @Override
    public ResourceLocation originalId() {
        return originalId;
    }

    @Override
    public String getDescriptionId() {
        return "block.scalpel.redacted";
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.scalpel.original", originalId.toString()).withStyle(ChatFormatting.GRAY));
    }
}
