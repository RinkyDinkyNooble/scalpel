package com.rinkynooble.scalpel.neoforge;

import com.rinkynooble.scalpel.common.Scalpel;
import com.rinkynooble.scalpel.neoforge.client.ScalpelClient;
import com.rinkynooble.scalpel.neoforge.data.CutLoot;
import com.rinkynooble.scalpel.neoforge.net.Handshake;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;

@Mod(Scalpel.MOD_ID)
public final class ScalpelNeoForge {
    public ScalpelNeoForge(IEventBus modBus, ModContainer container) {
        // FML 12 (NeoForge 26.3) has no COMMON type; LOCAL is the per-machine config read from config/.
        container.registerConfig(ModConfig.Type.LOCAL, NeoForgeSettings.SPEC, NeoForgeSettings.FILE_NAME);
        NeoForgeRemoved.register(modBus);
        CutLoot.register(modBus);
        modBus.addListener((FMLCommonSetupEvent event) -> Scalpel.afterRegistration());
        NeoForgeEvents.register(modBus, NeoForge.EVENT_BUS);
        modBus.addListener(Handshake::register);
        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            ScalpelClient.init(modBus);
        }
    }
}
