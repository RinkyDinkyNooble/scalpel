package com.rinkynooble.scalpel.core;

import com.rinkynooble.scalpel.core.rules.Rule;
import com.rinkynooble.scalpel.core.rules.RulesLoader;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A fingerprint of everything that must match between client and server: mod version, parsed rules
 * (so comments and spacing don't count) and the settings that change registration.
 */
public record RulesHash(String full, Map<String, String> perFile) {
    public String shortForm() {
        return full.substring(0, 8);
    }

    public static RulesHash compute(String modVersion, List<RulesLoader.RulesFile> files, Settings settings) {
        StringBuilder all = new StringBuilder();
        all.append("version=").append(modVersion).append('\n');
        all.append(settings.registrationFingerprint()).append('\n');
        Map<String, String> perFile = new LinkedHashMap<>();
        for (RulesLoader.RulesFile file : files) {
            StringBuilder one = new StringBuilder();
            for (Rule rule : file.rules()) {
                one.append(rule.canonical()).append('\n');
            }
            perFile.put(file.name(), sha256(one.toString()).substring(0, 8));
            all.append("file=").append(file.name()).append('\n').append(one);
        }
        return new RulesHash(sha256(all.toString()), perFile);
    }

    private static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
