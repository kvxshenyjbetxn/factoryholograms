package dev.factoryholograms;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;

import org.junit.Test;

public class LabelRulesTest {
    @Test
    public void nullAndWhitespaceTitlesBecomeEmpty() {
        assertEquals("", LabelRules.title(null));
        assertEquals("", LabelRules.title(" \t\r\n "));
    }

    @Test
    public void removesControlDeleteAndFormattingCharacters() {
        StringBuilder input = new StringBuilder(" A");
        for (int i = 0; i < 32; i++) input.append((char) i);
        input.append("B\u007fC\u00a7aD ");
        assertEquals("ABCaD", LabelRules.title(input.toString()));
    }

    @Test
    public void preservesInteriorSpacesAndUnicode() {
        assertEquals("Вхід  →  鉄 \ud83d\ude80", LabelRules.title("  Вхід  →  鉄 \ud83d\ude80  "));
    }

    @Test
    public void capsAcceptedCharactersRatherThanRawInputLength() {
        String expected = repeat('x', LabelRules.MAX_TITLE);
        assertEquals(expected, LabelRules.title(repeat('\n', 80) + expected + "overflow"));
        assertEquals(expected, LabelRules.title(expected));
        assertEquals(LabelRules.MAX_TITLE, LabelRules.title(repeat('x', 1000)).length());
    }

    @Test
    public void trimsAfterApplyingTheLengthLimit() {
        assertEquals(repeat('x', 63), LabelRules.title(" " + repeat('x', 64)));
        assertEquals("", LabelRules.title(repeat(' ', 64) + "outside limit"));
    }

    @Test
    public void lengthLimitCountsUtf16CodeUnits() {
        String input = repeat('x', 62) + "\ud83d\ude80";
        assertEquals(64, input.length());
        assertEquals(input, LabelRules.title(input + "overflow"));
    }

    @Test
    public void sanitizationIsIdempotent() {
        String once = LabelRules.title(" \u00a7Завод\n\t  → 鉄 " + repeat('x', 80));
        assertEquals(once, LabelRules.title(once));
    }

    @Test
    public void snapshotAndInventoryLimitsArePractical() {
        assertEquals(64, LabelRules.MAX_LABELS_PER_CHUNK);
        assertEquals(1024, LabelRules.MAX_ICON_BYTES);
        assertEquals(4, LabelRules.ICONS_PER_ROW);
        assertEquals(16, LabelRules.ICONS_PER_SIDE);
        assertEquals(32, LabelRules.ICON_SLOTS);
        assertEquals(6 * 1024, LabelRules.MAX_LABEL_ICON_BYTES);
        assertEquals(512 * 1024, LabelRules.MAX_SYNC_BYTES);
    }

    @Test public void offsetsAreFiniteAndBounded() {
        assertEquals(0.25F, LabelRules.OFFSET_STEP, 0);
        assertEquals(32, LabelRules.MAX_OFFSET, 0);
        assertEquals(-2.25F, LabelRules.offset(-2.25F), 0);
        assertEquals(32, LabelRules.offset(Float.MAX_VALUE), 0);
        assertEquals(-32, LabelRules.offset(-Float.MAX_VALUE), 0);
        for (float value : new float[] {Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            assertEquals(0, LabelRules.offset(value), 0);
        }
    }

    private static String repeat(char character, int count) {
        char[] characters = new char[count];
        Arrays.fill(characters, character);
        return new String(characters);
    }
}
