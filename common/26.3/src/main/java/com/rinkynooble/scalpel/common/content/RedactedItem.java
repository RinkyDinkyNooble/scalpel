package com.rinkynooble.scalpel.common.content;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/**
 * Stands in for a redacted item. No creative tab, no behaviour. Its model is {@code assets/<ns>/items/<id>.json} from
 * the placeholder pack (1.21.4: an item's model is named by its id).
 */
public class RedactedItem extends Item implements Redacted {
    private final Identifier originalId;

    public RedactedItem(Identifier originalId) {
        super(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, originalId)).overrideDescription("item.scalpel.redacted"));
        this.originalId = originalId;
    }

    @Override
    public Identifier originalId() {
        return originalId;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.scalpel.original", originalId.toString()).withStyle(ChatFormatting.GRAY));
    }
}
