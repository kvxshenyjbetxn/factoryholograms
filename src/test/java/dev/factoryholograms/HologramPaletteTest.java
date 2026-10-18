package dev.factoryholograms;

import static org.junit.Assert.*;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.junit.Test;

public class HologramPaletteTest {
    @Test public void paletteHasStableIdsAndExactRgbValues() {
        String[] ids = { "cyan", "white", "black", "red", "green", "blue", "yellow", "orange", "purple", "pink", "gray" };
        int[] colors = { 0x55DDFF, 0xFFFFFF, 0x000000, 0xFF5555, 0x55FF55, 0x5555FF, 0xFFFF55, 0xFFAA00, 0xAA55FF, 0xFF55AA, 0xAAAAAA };
        HologramPalette[] palette = HologramPalette.values();
        assertEquals(ids.length, palette.length);
        Set<String> uniqueIds = new HashSet<String>();
        Set<Integer> uniqueColors = new HashSet<Integer>();
        for (int i = 0; i < palette.length; i++) {
            assertEquals(ids[i], palette[i].id);
            assertEquals(colors[i], palette[i].rgb);
            assertEquals(ids[i].toUpperCase(Locale.ROOT), palette[i].name());
            assertEquals("config.factoryholograms.color." + ids[i], palette[i].languageKey());
            assertTrue(uniqueIds.add(palette[i].id));
            assertTrue(uniqueColors.add(palette[i].rgb));
        }
    }

    @Test public void lookupAcceptsCaseAndSurroundingWhitespace() {
        for (HologramPalette entry : HologramPalette.values()) {
            assertSame(entry, HologramPalette.fromId(entry.id));
            assertSame(entry, HologramPalette.fromId(" " + entry.id.toUpperCase(Locale.ROOT) + " "));
            assertSame(entry, HologramPalette.fromRgb(entry.rgb));
        }
    }

    @Test public void unknownIdsAndCustomRgbDoNotApproximatePaletteColors() {
        for (String value : new String[] { null, "", " ", "unknown", "grey", "#000000", "55DDFF" }) {
            assertNull(HologramPalette.fromId(value));
        }
        for (int rgb : new int[] { 0x000001, 0x123456, 0xFFAA33, 0xFFFFFE, -1, 0x1000000 }) {
            assertNull(HologramPalette.fromRgb(rgb));
        }
    }

    @Test public void panelBackgroundPreservesSelectedColorIncludingBlack() {
        for (HologramPalette entry : HologramPalette.values()) {
            assertEquals(entry.rgb, HologramPalette.panelBackground(entry.rgb));
        }
        for (int rgb : new int[] { 0x000001, 0x101820, 0x222222, 0x000080, 0x800000 }) {
            assertEquals(rgb, HologramPalette.panelBackground(rgb));
        }
    }

    @Test public void panelBackgroundUsesRgbOnlyAndReturnsNoAlpha() {
        for (HologramPalette entry : HologramPalette.values()) {
            int background = HologramPalette.panelBackground(entry.rgb);
            assertEquals(0, background & 0xFF000000);
            assertEquals(background, HologramPalette.panelBackground(entry.rgb | 0xFF000000));
        }
    }

}
