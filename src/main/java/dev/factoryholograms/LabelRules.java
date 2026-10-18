package dev.factoryholograms;

/** Shared bounds for GUI input, saved data and server updates. */
public final class LabelRules {
    public static final int MAX_TITLE = 64;
    public static final int ICONS_PER_ROW = 4;
    public static final int ICONS_PER_SIDE = 16;
    public static final int ICON_SLOTS = ICONS_PER_SIDE * 2;
    public static final float BASE_Z = 1.0F;
    public static final float MAX_OFFSET = 32.0F;
    public static final float OFFSET_STEP = 0.25F;
    public static final int MAX_LABELS_PER_CHUNK = 64;
    public static final int MAX_ICON_BYTES = 1024;
    public static final int MAX_LABEL_ICON_BYTES = 6 * MAX_ICON_BYTES;
    public static final int MAX_SYNC_BYTES = 512 * 1024;

    private LabelRules() {}

    public static float offset(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) return 0.0F;
        return Math.max(-MAX_OFFSET, Math.min(MAX_OFFSET, value));
    }

    public static float angle(float value, float limit) {
        if (Float.isNaN(value) || Float.isInfinite(value)) return 0.0F;
        return Math.max(-limit, Math.min(limit, value));
    }

    public static String title(String value) {
        if (value == null) return "";
        StringBuilder clean = new StringBuilder();
        for (int i = 0; i < value.length() && clean.length() < MAX_TITLE; i++) {
            char c = value.charAt(i);
            if (c >= 32 && c != 127 && c != '\u00a7') clean.append(c);
        }
        return clean.toString().trim();
    }
}
