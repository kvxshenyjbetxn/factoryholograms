package dev.factoryholograms;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/** Filesystem-safe local identities; never resolves a hostname or accesses a server/save folder. */
public final class LocalWorldKey {
    private LocalWorldKey() {}

    /** Case-insensitive server address, with the default port and optional IPv6 brackets removed. */
    public static String server(String address) {
        String normalized = identity(address).toLowerCase(Locale.ROOT);
        if (normalized.startsWith("[")) {
            int end = normalized.indexOf(']');
            if (end > 1) {
                String suffix = normalized.substring(end + 1);
                if (suffix.isEmpty() || ":25565".equals(suffix)) {
                    normalized = normalized.substring(1, end);
                }
            }
        } else if (normalized.indexOf(':') == normalized.lastIndexOf(':') && normalized.endsWith(":25565")) {
            normalized = normalized.substring(0, normalized.length() - 6);
        }
        if (normalized.isEmpty()) throw new IllegalArgumentException("Server address must not be blank");
        return hash("server\u0000" + normalized);
    }

    /** Folder names remain case-sensitive, independent of the host operating system. */
    public static String singleplayer(String folderName) {
        return hash("singleplayer\u0000" + identity(folderName));
    }

    public static File dataFile(File mcDataDir, boolean singleplayer, String identity, int dimension) {
        if (mcDataDir == null) throw new IllegalArgumentException("Minecraft data directory is required");
        String key = singleplayer ? singleplayer(identity) : server(identity);
        File category = new File(new File(mcDataDir, "factoryholograms"), singleplayer ? "singleplayer" : "servers");
        return new File(new File(category, key), "DIM" + dimension + ".dat");
    }

    private static String identity(String value) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException("World identity must not be blank");
        return value.trim();
    }

    private static String hash(String identity) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8));
            char[] hex = new char[digest.length * 2];
            char[] alphabet = "0123456789abcdef".toCharArray();
            for (int i = 0; i < digest.length; i++) {
                hex[i * 2] = alphabet[(digest[i] & 255) >>> 4];
                hex[i * 2 + 1] = alphabet[digest[i] & 15];
            }
            return new String(hex);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
