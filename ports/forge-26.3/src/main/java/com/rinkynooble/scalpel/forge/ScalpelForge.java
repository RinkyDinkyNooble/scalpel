package com.rinkynooble.scalpel.forge;

import com.rinkynooble.scalpel.common.Scalpel;
import com.rinkynooble.scalpel.forge.client.ScalpelClient;
import com.rinkynooble.scalpel.forge.data.CutLoot;
import com.rinkynooble.scalpel.forge.net.Handshake;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * Forge 62-64 (Minecraft 26.1-26.1.2). EventBus 7: mod events are reached through the mod's bus group
 * ({@code FMLCommonSetupEvent.getBus(group)}, DeferredRegister), game events through each event's static {@code BUS}.
 */
@Mod(Scalpel.MOD_ID)
public final class ScalpelForge {
    public ScalpelForge(FMLJavaModLoadingContext context) {
        BusGroup modBusGroup = context.getModBusGroup();
        context.registerConfig(ModConfig.Type.COMMON, ForgeSettings.SPEC, ForgeSettings.FILE_NAME);
        ForgeRemoved.register(modBusGroup);
        CutLoot.register(modBusGroup);
        FMLCommonSetupEvent.getBus(modBusGroup).addListener(event -> Scalpel.afterRegistration());
        ForgeEvents.register();
        Handshake.register();
        if (FMLEnvironment.dist.isClient()) {
            ScalpelClient.init();
        }
    }
}
