package dev.factoryholograms;

import java.io.File;
import java.util.Arrays;
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
    public static final int DEFAULT_TEXT_COLOR = HologramPalette.WHITE.rgb;
    public static boolean enabled = true;
    public static boolean autoRotate = true;
    public static boolean hoverTooltips = true;
    public static int renderDistance =DEFAULT_DISTANCE;
    public static float opacity = DEFAULT_OPACITY;
    public static int color = DEFAULT_COLOR;
    public static int textColor = DEFAULT_TEXT_COLOR;
    public static final String LINK_FOCUS = "focus", LINK_ALL = "all", LINK_OFF = "off";
    public static final String[] LINK_MODES = {LINK_FOCUS, LINK_ALL, LINK_OFF};
    public static String linkMode = LINK_FOCUS;
    public static int linkColor = HologramPalette.ORANGE.rgb;
    public static boolean linksThroughWalls = true;
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
        Property rotationProperty = configuration.get("display", "autoRotate", true, "Automatically face the camera. Disable to use each hologram's saved yaw and pitch.");
        rotationProperty.setLanguageKey("config.factoryholograms.autoRotate");
        autoRotate = rotationProperty.getBoolean(true);
        Property hoverProperty = configuration.get("display", "hoverTooltips", true, "Show an item tooltip when aiming the crosshair at a hologram icon, and allow NEI recipe/usage keys on it.");
        hoverProperty.setLanguageKey("config.factoryholograms.hoverTooltips");
        hoverTooltips = hoverProperty.getBoolean(true);
        Property distanceProperty =configuration.get("display", "renderDistance", DEFAULT_DISTANCE, "Global visibility distance in blocks (4-64).");
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
        color = syncColor("color", HologramPalette.CYAN, "Global panel background and border color. Named colors and six-digit RGB hex are supported.");
        textColor = syncColor("textColor", HologramPalette.WHITE, "Global title and arrow color. Named colors and six-digit RGB hex are supported.");
        Property modeProperty = configuration.get("display", "linkMode", LINK_FOCUS, "Link lines between holograms whose output item is another's input: focus = only for the hologram you look at, all, or off.");
        modeProperty.setValidValues(LINK_MODES);
        modeProperty.setLanguageKey("config.factoryholograms.linkMode");
        String mode = modeProperty.getString();
        linkMode = Arrays.asList(LINK_MODES).contains(mode) ? mode : LINK_FOCUS;
        modeProperty.set(linkMode);
        linkColor = syncColor("linkColor", HologramPalette.ORANGE, "Color of link lines between holograms. Named colors and six-digit RGB hex are supported.");
        Property wallsProperty = configuration.get("display", "linksThroughWalls", true, "Also show faint link lines through blocks.");
        wallsProperty.setLanguageKey("config.factoryholograms.linksThroughWalls");
        linksThroughWalls = wallsProperty.getBoolean(true);
        if (configuration.hasChanged()) configuration.save();
    }

    public static void setLinkMode(String mode) {
        linkMode = Arrays.asList(LINK_MODES).contains(mode) ? mode : LINK_FOCUS;
        if (configuration != null) {
            configuration.get("display", "linkMode", LINK_FOCUS).set(linkMode);
            configuration.save();
        }
    }

    public static void setEnabled(boolean value) {
        enabled = value;
        if (configuration != null) {
            configuration.get("display", "enabled", true).set(value);
            configuration.save();
        }
    }

    private static int syncColor(String name, HologramPalette defaultColor, String comment) {
        Property property = configuration.get("display", name, defaultColor.id, comment);
        property.setLanguageKey("config.factoryholograms." + name);
        int rgb = parseColor(property.getString(), defaultColor.rgb);
        HologramPalette namedColor = HologramPalette.fromRgb(rgb);
        property.set(namedColor == null ? String.format(Locale.ROOT, "%06X", rgb) : namedColor.id);
        return rgb;
    }

    static int parseColor(String value) {
        return parseColor(value, DEFAULT_COLOR);
    }

    private static int parseColor(String value, int fallback) {
        if (value == null) return fallback;
        HologramPalette namedColor = HologramPalette.fromId(value);
        if (namedColor != null) return namedColor.rgb;
        String text = value.trim();
        if (text.startsWith("#")) text = text.substring(1);
        if (!text.matches("[0-9a-fA-F]{6}")) return fallback;
        return Integer.parseInt(text, 16);
    }
}
