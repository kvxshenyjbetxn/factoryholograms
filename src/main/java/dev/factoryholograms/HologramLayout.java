package dev.factoryholograms;

/** Pixel geometry shared by the compact world panel and its headless tests. */
public final class HologramLayout {
    public final int width, height, inputX, outputX, arrowX, arrowY;
    public final boolean arrow;

    public HologramLayout(int inputs, int outputs, int titleWidth) {
        int inputWidth = sideWidth(inputs);
        int outputWidth = sideWidth(outputs);
        arrow = inputs > 0 && outputs > 0;
        int gap = arrow ? 22 : 0;
        int contentWidth = inputWidth + gap + outputWidth;
        width = Math.max(32, Math.max(titleWidth + 12, contentWidth + 16));
        int rows = Math.max(rows(inputs), rows(outputs));
        height = rows == 0 ? 20 : 22 + rows * 18;
        inputX = (width - contentWidth) / 2;
        outputX = inputX + inputWidth + gap;
        arrowX = inputX + inputWidth + 6;
        arrowY = 18 + (rows * 18 - 9) / 2;
    }

    private static int sideWidth(int count) {
        return count == 0 ? 0 : Math.min(LabelRules.ICONS_PER_ROW, count) * 18 - 2;
    }

    public static int rows(int count) { return (count + LabelRules.ICONS_PER_ROW - 1) / LabelRules.ICONS_PER_ROW; }
    public static int iconX(int start, int index, int count) {
        int row = index / LabelRules.ICONS_PER_ROW;
        int columns = Math.min(LabelRules.ICONS_PER_ROW, count);
        int rowColumns = Math.min(LabelRules.ICONS_PER_ROW, count - row * LabelRules.ICONS_PER_ROW);
        return start + (columns - rowColumns) * 9 + index % LabelRules.ICONS_PER_ROW * 18;
    }
    public static int iconY(int index) { return 18 + index / LabelRules.ICONS_PER_ROW * 18; }
}
