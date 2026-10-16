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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * Stands in for a redacted block: one shared model, no drops, no behaviour. It has the original's block states (the
 * same properties and default values), because other mods' code and data read them by name: SecurityCraft's model
 * setup reads its interface's {@code sender}, ResourcefulLib's highlight files name a block's {@code facing}.
 */
public class RedactedBlock extends Block implements Redacted {
    /** The original's properties, for {@link #createBlockStateDefinition}, which runs inside Block's constructor. */
    private static final ThreadLocal<Collection<Property<?>>> PROPERTIES = new ThreadLocal<>();

    private final ResourceLocation originalId;

    /** A placeholder with one state: for a block that was never built (a removed id's ghost). */
    public RedactedBlock(ResourceLocation originalId) {
        this(originalId, null);
    }

    private RedactedBlock(ResourceLocation originalId, @Nullable Block original) {
        super(BlockBehaviour.Properties.of().instabreak().noLootTable().sound(SoundType.STONE));
        this.originalId = originalId;
        if (original != null && !getStateDefinition().getProperties().isEmpty()) {
            BlockState state = defaultBlockState();
            for (Property<?> property : getStateDefinition().getProperties()) {
                state = copy(state, original.defaultBlockState(), property);
            }
            registerDefaultState(state);
        }
    }

    /** A placeholder with {@code original}'s states. */
    public static RedactedBlock copying(ResourceLocation originalId, Block original) {
        PROPERTIES.set(original.getStateDefinition().getProperties());
        try {
            return new RedactedBlock(originalId, original);
        } finally {
            PROPERTIES.remove();
        }
    }

    private static <T extends Comparable<T>> BlockState copy(BlockState to, BlockState from, Property<T> property) {
        return to.setValue(property, from.getValue(property));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        Collection<Property<?>> properties = PROPERTIES.get();
        if (properties != null && !properties.isEmpty()) {
            builder.add(properties.toArray(Property<?>[]::new));
        }
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
