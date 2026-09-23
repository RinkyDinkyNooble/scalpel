package com.rinkynooble.scalpel.forge;

import com.rinkynooble.scalpel.core.ScalpelCore;
import com.rinkynooble.scalpel.core.ScalpelLog;
import com.rinkynooble.scalpel.core.Settings;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Holds the one {@link ScalpelCore} for this game. Created on first use, which is the first registry entry. */
public final class Scalpel {
    public static final String MOD_ID = "scalpel";
    public static final Logger LOGGER = LogManager.getLogger("Scalpel");
    public static final String PORT = "forge-1.20.1";

    private static volatile ScalpelCore core;

    private Scalpel() {
    }

    public static ScalpelCore core() {
        ScalpelCore c = core;
        if (c == null) {
            synchronized (Scalpel.class) {
                c = core;
                if (c == null) {
                    c = core = start();
                }
            }
        }
        return c;
    }

    private static ScalpelCore start() {
        ScalpelLog.Mirror mirror = new ScalpelLog.Mirror() {
            @Override
            public void info(String message) {
                LOGGER.info(message);
            }

            @Override
            public void warn(String message) {
                LOGGER.warn(message);
            }

            @Override
            public void error(String message) {
                LOGGER.error(message);
            }
        };
        Settings settings = ForgeSettings.readEarly(FMLPaths.CONFIGDIR.get(), LOGGER::warn);
        String version = modVersion();
        ScalpelCore started = ScalpelCore.start(
                FMLPaths.CONFIGDIR.get().resolve(MOD_ID),
                FMLPaths.GAMEDIR.get().resolve("logs"),
                version,
                version + " (" + PORT + ")",
                settings,
                mirror);
        started.side(FMLEnvironment.dist == Dist.CLIENT ? "client" : "dedicated server");
        return started;
    }

    private static String modVersion() {
        try {
            return FMLLoader.getLoadingModList().getModFileById(MOD_ID).getMods().get(0).getVersion().toString();
        } catch (RuntimeException e) {
            return "unknown";
        }
    }
}
