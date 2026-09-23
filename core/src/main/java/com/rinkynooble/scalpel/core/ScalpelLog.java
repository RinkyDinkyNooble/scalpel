package com.rinkynooble.scalpel.core;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Scalpel's own log, {@code logs/scalpel.log}. Detail lines only go here; summaries, warnings and errors
 * are also passed to the game log through the {@link Mirror}. Every line is flushed so nothing is lost if the game crashes.
 */
public final class ScalpelLog {
    public interface Mirror {
        void info(String message);

        void warn(String message);

        void error(String message);
    }

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final Mirror mirror;
    private BufferedWriter writer;

    public ScalpelLog(Path file, Mirror mirror) {
        this.mirror = mirror;
        if (file != null) {
            try {
                Files.createDirectories(file.getParent());
                writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8);
            } catch (IOException e) {
                mirror.warn("Could not open " + file + ": " + e.getMessage());
            }
        }
    }

    /** A detail line, written to scalpel.log only. */
    public void detail(String message) {
        write("INFO", message);
    }

    /** A summary line, written to both logs. */
    public void info(String message) {
        write("INFO", message);
        mirror.info(message);
    }

    public void warn(String message) {
        write("WARN", message);
        mirror.warn(message);
    }

    public void error(String message) {
        write("ERROR", message);
        mirror.error(message);
    }

    private synchronized void write(String level, String message) {
        if (writer == null) {
            return;
        }
        try {
            writer.write('[' + LocalTime.now().format(TIME) + "] [" + level + "] " + message);
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            writer = null;
            mirror.warn("Stopped writing scalpel.log: " + e.getMessage());
        }
    }
}
