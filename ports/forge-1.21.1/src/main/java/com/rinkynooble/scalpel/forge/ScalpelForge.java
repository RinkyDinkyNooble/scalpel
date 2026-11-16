package com.rinkynooble.scalpel.forge;

import com.rinkynooble.scalpel.common.Scalpel;
import com.rinkynooble.scalpel.forge.client.ScalpelClient;
import com.rinkynooble.scalpel.forge.data.CutLoot;
import com.rinkynooble.scalpel.forge.net.Handshake;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod(Scalpel.MOD_ID)
public final class ScalpelForge {
    /**
     * No-argument constructor with the static context getters: Forge 52 deprecates them in favour of a constructor
     * parameter, but Forge 51 only calls a no-argument constructor, and one jar serves both.
     */
    @SuppressWarnings("removal")
    public ScalpelForge() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ForgeSettings.SPEC, ForgeSettings.FILE_NAME);
        ForgeRemoved.register(modBus);
        CutLoot.register(modBus);
        modBus.addListener((FMLCommonSetupEvent event) -> Scalpel.afterRegistration());
        ForgeEvents.register(modBus, MinecraftForge.EVENT_BUS);
        Handshake.register();
        if (FMLEnvironment.dist.isClient()) {
            ScalpelClient.init(modBus);
        }
    }
}
