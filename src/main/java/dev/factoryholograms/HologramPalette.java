package dev.factoryholograms;

import java.util.Locale;

/** Named RGB colors shared by config and rendering, without client dependencies. */
public enum HologramPalette {
    CYAN(0x55DDFF),
    WHITE(0xFFFFFF),
    BLACK(0x000000),
    RED(0xFF5555),
    GREEN(0x55FF55),
    BLUE(0x5555FF),
    YELLOW(0xFFFF55),
    ORANGE(0xFFAA00),
    PURPLE(0xAA55FF),
    PINK(0xFF55AA),
    GRAY(0xAAAAAA);

    public final String id;
    public final int rgb;

    HologramPalette(int rgb) {
        this.id = name().toLowerCase(Locale.ROOT);
        this.rgb = rgb;
    }

    public String languageKey() {
        return "config.factoryholograms.color." + id;
    }

    /** Returns null when the value is not a named palette color. */
    public static HologramPalette fromId(String value) {
        if (value == null) return null;
        String id = value.trim();
        for (HologramPalette entry : values()) {
            if (entry.id.equalsIgnoreCase(id)) return entry;
        }
        return null;
    }

    /** Returns null for custom RGB colors, so they can be preserved rather than approximated. */
    public static HologramPalette fromRgb(int rgb) {
        for (HologramPalette entry : values()) {
            if (entry.rgb == rgb) return entry;
        }
        return null;
    }

    /** Returns an opaque RGB background; the renderer supplies its own alpha. */
    public static int panelBackground(int rgb) {
        int red = (rgb >>> 16) & 0xFF;
        int green = (rgb >>> 8) & 0xFF;
        int blue = rgb & 0xFF;
        // Weighted brightness keeps black and dark custom colors readable on a light panel.
        return red * 299 + green * 587 + blue * 114 < 128000 ? 0xEEEEEE : 0x101820;
    }
}
