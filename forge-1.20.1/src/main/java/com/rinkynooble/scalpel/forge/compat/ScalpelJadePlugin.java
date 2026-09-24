package com.rinkynooble.scalpel.forge.compat;

import com.rinkynooble.scalpel.forge.Scalpel;
import com.rinkynooble.scalpel.forge.content.RedactedBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

/** Jade shows which id a redacted block stands in for. Only loaded when Jade is installed. */
@WailaPlugin
public class ScalpelJadePlugin implements IWailaPlugin {
    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(RedactedBlockTooltip.INSTANCE, RedactedBlock.class);
    }

    public enum RedactedBlockTooltip implements IBlockComponentProvider {
        INSTANCE;

        private static final ResourceLocation UID = new ResourceLocation(Scalpel.MOD_ID, "redacted_block");

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            if (accessor.getBlock() instanceof RedactedBlock block) {
                tooltip.add(Component.translatable("tooltip.scalpel.original", block.originalId().toString()).withStyle(ChatFormatting.GRAY));
            }
        }

        @Override
        public ResourceLocation getUid() {
            return UID;
        }
    }
}
