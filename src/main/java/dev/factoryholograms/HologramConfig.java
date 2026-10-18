package dev.factoryholograms;

import java.io.File;
import java.util.Locale;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

/** Client display preferences apply to every label; shared world data contains no styling. */
public final class HologramConfig {
    public static final int DEFAULT_DISTANCE = 12;
    public static final int MIN_DISTANCE = 4;
    public static final int MAX_DISTANCE = 64;
    public static final float DEFAULT_OPACITY = 0.85F;
    public static final int DEFAULT_COLOR = HologramPalette.CYAN.rgb;
    public static boolean enabled = true;
    public static int renderDistance = DEFAULT_DISTANCE;
    public static float opacity = DEFAULT_OPACITY;
    public static int color = DEFAULT_COLOR;
    private static Configuration configuration;

    private HologramConfig() {}

    public static void load(File file) {
        configuration = new Configuration(file);
        configuration.load();
        sync();
    }

    public static Configuration getConfiguration() { return configuration; }

    public static void sync() {
        if (configuration == null) return;
        configuration.getCategory("display").setComment("Client-only settings for ALL factory holograms. No server restart required when edited in the Mods config screen.");
        Property enabledProperty = configuration.get("display", "enabled", true, "Show all holograms. Also toggled with H or the editor's All button.");
        enabledProperty.setLanguageKey("config.factoryholograms.enabled");
        enabled = enabledProperty.getBoolean(true);
        Property distanceProperty = configuration.get("display", "renderDistance", DEFAULT_DISTANCE, "Global visibility distance in blocks (4-64).");
        distanceProperty.setMinValue(MIN_DISTANCE).setMaxValue(MAX_DISTANCE);
        distanceProperty.setLanguageKey("config.factoryholograms.renderDistance");
        renderDistance = Math.max(MIN_DISTANCE, Math.min(MAX_DISTANCE, distanceProperty.getInt(DEFAULT_DISTANCE)));
        distanceProperty.set(renderDistance);
        Property opacityProperty = configuration.get("display", "opacity", (double) DEFAULT_OPACITY, "Global opacity multiplier: 0.0 = invisible, 1.0 = maximum opacity.");
        opacityProperty.setMinValue(0.0).setMaxValue(1.0);
        opacityProperty.setLanguageKey("config.factoryholograms.opacity");
        double value = opacityProperty.getDouble(DEFAULT_OPACITY);
        opacity = Double.isNaN(value) || Double.isInfinite(value) ? DEFAULT_OPACITY : (float) Math.max(0.0, Math.min(1.0, value));
        opacityProperty.set((double) opacity);
        Property colorProperty = configuration.get("display", "color", HologramPalette.CYAN.id, "Global accent/text color. Choose a named color in Mods Config; existing six-digit RGB hex colors are also supported.");
        colorProperty.setLanguageKey("config.factoryholograms.color");
        color = parseColor(colorProperty.getString());
        HologramPalette namedColor = HologramPalette.fromRgb(color);
        colorProperty.set(namedColor == null ? String.format(Locale.ROOT, "%06X", color) : namedColor.id);
        if (configuration.hasChanged()) configuration.save();
    }

    public static void setEnabled(boolean value) {
        enabled = value;
        if (configuration != null) {
            configuration.get("display", "enabled", true).set(value);
            configuration.save();
        }
    }

    static int parseColor(String value) {
        if (value == null) return DEFAULT_COLOR;
        HologramPalette namedColor = HologramPalette.fromId(value);
        if (namedColor != null) return namedColor.rgb;
        String text = value.trim();
        if (text.startsWith("#")) text = text.substring(1);
        if (!text.matches("[0-9a-fA-F]{6}")) return DEFAULT_COLOR;
        return Integer.parseInt(text, 16);
    }
}
