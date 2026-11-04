package dev.factoryholograms;

/** Pixel geometry shared by the compact world panel and its headless tests. */
public final class HologramLayout {
    public static final int ROW = 18;
    public static final int SEPARATOR = 7;
    public static final int NAME_HEADER = 10;
    private static final int GAP = 22;

    public final int width, height, inputX, outputX, arrowX, arrowY;
    /** True when the first band shows an arrow (always the case for a single recipe with both sides). */
    public final boolean arrow;

    private final int[][] inputSlots, outputSlots;
    private final int[] bandTop, bandRows, bandHeader;
    private final int[] slotX = new int[LabelRules.ICON_SLOTS];
    private final int[] slotY = new int[LabelRules.ICON_SLOTS];
    private final boolean[] present = new boolean[LabelRules.ICON_SLOTS];

    /** Single-recipe convenience: inputs use slots 0.., outputs use slots 16.. . */
    public HologramLayout(int inputs, int outputs, int titleWidth) {
        this(inputs + outputs == 0 ? new int[0][] : new int[][] {sequence(0, inputs)},
            inputs + outputs == 0 ? new int[0][] : new int[][] {sequence(LabelRules.ICONS_PER_SIDE, outputs)}, titleWidth);
    }

    /** One entry per visible recipe band: the global slot indices of its inputs and outputs, in display order. */
    public HologramLayout(int[][] inputSlots, int[][] outputSlots, int titleWidth) {
        this(inputSlots, outputSlots, new int[inputSlots.length], titleWidth);
    }

    /** As above, plus one name width per band (0 = unnamed, no header). */
    public HologramLayout(int[][] inputSlots, int[][] outputSlots, int[] nameWidths, int titleWidth) {
        this.inputSlots = inputSlots;
        this.outputSlots = outputSlots;
        int bands = inputSlots.length;
        int inputWidth = 0, outputWidth = 0;
        bandTop = new int[bands];
        bandRows = new int[bands];
        bandHeader = new int[bands];
        int totalHeight = 0;
        int nameWidth = 0;
        int top = titleWidth > 0 ? 18 : 4;
        for (int b = 0; b < bands; b++) {
            inputWidth = Math.max(inputWidth, sideWidth(inputSlots[b].length));
            outputWidth = Math.max(outputWidth, sideWidth(outputSlots[b].length));
            bandRows[b] = Math.max(rows(inputSlots[b].length), rows(outputSlots[b].length));
            bandHeader[b] = nameWidths[b] > 0 ? NAME_HEADER : 0;
            nameWidth = Math.max(nameWidth, nameWidths[b]);
            bandTop[b] = top + totalHeight + b * SEPARATOR;
            totalHeight += bandHeader[b] + bandRows[b] * ROW;
        }
        int gap = inputWidth > 0 && outputWidth > 0 ? GAP : 0;
        int contentWidth = inputWidth + gap + outputWidth;
        width = Math.max(32, Math.max(Math.max(titleWidth + 12, contentWidth + 16), nameWidth > 0 ? nameWidth + 12 : 0));
        height = bands == 0 ? 20 : top + 4 + totalHeight + (bands - 1) * SEPARATOR;
        inputX = (width - contentWidth) / 2;
        outputX = inputX + inputWidth + gap;
        arrowX = inputX + inputWidth + 6;
        arrow = bands > 0 && hasArrow(0);
        arrowY = bands > 0 ? arrowY(0) : 18;
        for (int b = 0; b < bands; b++) {
            place(inputSlots[b], inputX + (inputWidth - sideWidth(inputSlots[b].length)) / 2, bandTop[b] + bandHeader[b]);
            place(outputSlots[b], outputX + (outputWidth - sideWidth(outputSlots[b].length)) / 2, bandTop[b] + bandHeader[b]);
        }
    }

    private void place(int[] slots, int start, int top) {
        for (int i = 0; i < slots.length; i++) {
            int slot = slots[i];
            if (slot < 0 || slot >= slotX.length) continue;
            present[slot] = true;
            slotX[slot] = iconX(start, i, slots.length);
            slotY[slot] = top + i / LabelRules.ICONS_PER_ROW * ROW;
        }
    }

    private static int[] sequence(int start, int count) {
        int[] result = new int[count];
        for (int i = 0; i < count; i++) result[i] = start + i;
        return result;
    }

    public int bandCount() { return bandTop.length; }
    public int bandTop(int band) { return bandTop[band]; }
    public int bandRows(int band) { return bandRows[band]; }
    public boolean hasArrow(int band) { return inputSlots[band].length > 0 && outputSlots[band].length > 0; }
    public int arrowY(int band) { return bandTop[band] + bandHeader[band] + (bandRows[band] * ROW - 9) / 2; }
    public boolean hasName(int band) { return bandHeader[band] > 0; }
    public int nameY(int band) { return bandTop[band]; }
    public int[] inputSlots(int band) { return inputSlots[band]; }
    public int[] outputSlots(int band) { return outputSlots[band]; }

    /** Y of the 1px separator line between band and band + 1. */
    public int separatorY(int band) { return bandTop[band] + bandHeader[band] + bandRows[band] * ROW + 3; }

    /** Band containing panel-pixel row py (header through separator), or -1 for the title area or outside. */
    public int bandAt(double py) {
        int last = bandTop.length - 1;
        for (int b = 0; b <= last; b++) {
            if (py >= bandTop[b] && py < (b == last ? height : bandTop[b + 1])) return b;
        }
        return -1;
    }

    public boolean hasSlot(int slot) { return slot >= 0 && slot < present.length && present[slot]; }
    /** Icon position for a GLOBAL slot index; only valid when {@link #hasSlot(int)}. */
    public int slotX(int slot) { return slotX[slot]; }
    public int slotY(int slot) { return slotY[slot]; }

    /** All displayed global slots, bands in order, inputs before outputs. */
    public int[] allSlots() {
        int count = 0;
        for (int b = 0; b < bandCount(); b++) count += inputSlots[b].length + outputSlots[b].length;
        int[] result = new int[count];
        int index = 0;
        for (int b = 0; b < bandCount(); b++) {
            for (int slot : inputSlots[b]) result[index++] = slot;
            for (int slot : outputSlots[b]) result[index++] = slot;
        }
        return result;
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
