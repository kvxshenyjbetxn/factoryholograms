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
        // Text has its own color setting; never invert the selected panel color.
        return rgb & 0xFFFFFF;
    }
}
