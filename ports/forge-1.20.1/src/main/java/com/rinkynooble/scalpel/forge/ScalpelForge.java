package com.rinkynooble.scalpel.forge;

import com.rinkynooble.scalpel.common.Scalpel;
import com.rinkynooble.scalpel.forge.client.ScalpelClient;
import com.rinkynooble.scalpel.forge.data.CutLoot;
import com.rinkynooble.scalpel.forge.net.Handshake;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(Scalpel.MOD_ID)
public final class ScalpelForge {
    public ScalpelForge() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ForgeSettings.SPEC, ForgeSettings.FILE_NAME);
        ForgeRemoved.register(modBus);
        CutLoot.register(modBus);
        modBus.addListener((FMLCommonSetupEvent event) -> Scalpel.afterRegistration());
        ForgeEvents.register(modBus, MinecraftForge.EVENT_BUS);
        Handshake.register();
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ScalpelClient.init(modBus));
    }
}
