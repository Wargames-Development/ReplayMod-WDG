package com.replaymod.replay.compat;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** DH caches may use either encoded world keys or readable names with a key sidecar. */
public final class LodCacheFolders {
    private LodCacheFolders() { }

    public static String key(File folder) {
        File marker = new File(folder, ".dh-level-key");
        String key = folder.getName();
        if (marker.exists()) {
            // A bad sidecar must not silently fall back to a potentially different folder identity.
            try (InputStream input = new FileInputStream(marker)) {
                byte[] bytes = new byte[512];
                int length = 0, count;
                while (length < bytes.length && (count = input.read(bytes, length, bytes.length - length)) != -1) length += count;
                if (input.read() != -1) return null;
                key = new String(bytes, 0, length, StandardCharsets.UTF_8).trim();
            } catch (IOException unreadable) { return null; }
        }
        return key.matches("[a-zA-Z0-9_@:-]{1,150}") ? key.replace(":", "@@") : null;
    }

    public static Integer dimension(File folder) {
        String key = key(folder);
        if (key == null) return null;
        int separator = key.lastIndexOf("@@");
        if (separator < 0) return null;
        try { return Integer.parseInt(key.substring(separator + 2)); }
        catch (NumberFormatException invalid) { return null; }
    }

    public static List<File> candidates(File root) {
        List<File> result = new ArrayList<>();
        File[] servers = root.listFiles(File::isDirectory);
        if (servers != null) for (File server : servers) {
            if (server.getName().equals("REPLAY") || server.getName().startsWith("ReplayModReplay")) continue;
            File[] levels = server.listFiles(File::isDirectory);
            if (levels != null) for (File level : levels) {
                if (new File(level, "DistantHorizons.sqlite").isFile() && dimension(level) != null) result.add(level);
            }
        }
        result.sort(Comparator.comparing(File::getPath));
        return result;
    }

    public static Map<Integer, File> match(File root, Map<Integer, Set<String>> identities) {
        Map<Integer, File> result = new HashMap<>();
        List<File> candidates = candidates(root);
        for (Map.Entry<Integer, Set<String>> identity : identities.entrySet()) {
            if (identity.getValue().size() != 1) continue;
            String key = identity.getValue().iterator().next();
            File match = null;
            for (File folder : candidates) if (key.equals(key(folder))) {
                if (match != null) { match = null; break; } // Duplicate identities still require an explicit choice.
                match = folder;
            }
            if (match != null) result.put(identity.getKey(), match);
        }
        return result;
    }
}
