package dev.factoryholograms;

import static org.junit.Assert.*;
import org.junit.Test;

public class HologramLayoutTest {
    @Test public void oneInOneOutUsesOnlyTwoIconCells() {
        HologramLayout layout = new HologramLayout(1, 1, 20);
        assertEquals(70, layout.width); assertEquals(40, layout.height); assertTrue(layout.arrow);
        assertEquals(8, layout.inputX); assertEquals(46, layout.outputX);
    }

    @Test public void fifthItemStartsSecondRowWithoutGrowingWidth() {
        HologramLayout four = new HologramLayout(4, 1, 20);
        HologramLayout five = new HologramLayout(5, 1, 20);
        assertEquals(four.width, five.width); assertEquals(four.height + 18, five.height);
        assertEquals(five.inputX + 27, HologramLayout.iconX(five.inputX, 4, 5));
        assertEquals(36, HologramLayout.iconY(4));
        assertEquals(five.inputX + 54, HologramLayout.iconX(five.inputX, 3, 5));
        assertEquals(18, HologramLayout.iconY(3));
    }

    @Test public void emptySideDoesNotReserveCellsOrArrow() {
        HologramLayout input = new HologramLayout(1, 0, 20);
        HologramLayout output = new HologramLayout(0, 1, 20);
        assertFalse(input.arrow); assertFalse(output.arrow);
        assertEquals(32, input.width); assertEquals(input.width, output.width);
        assertEquals(8, input.inputX); assertEquals(8, output.outputX);
    }

    @Test public void titleOnlyDoesNotReserveIconRows() {
        HologramLayout layout = new HologramLayout(0, 0, 100);
        assertEquals(112, layout.width); assertEquals(20, layout.height); assertFalse(layout.arrow);
    }

    @Test public void fullInventoriesHaveFourRowsEachWithinPanel() {
        HologramLayout layout = new HologramLayout(16, 16, 20);
        assertEquals(4, HologramLayout.rows(16)); assertEquals(178, layout.width); assertEquals(94, layout.height);
        for (int i = 0; i < 16; i++) {
            assertTrue(HologramLayout.iconX(layout.inputX, i, 16) >= 0);
            assertTrue(HologramLayout.iconX(layout.outputX, i, 16) + 16 < layout.width);
            assertTrue(HologramLayout.iconY(i) + 16 < layout.height);
        }
    }

    @Test public void incompleteRowsStayCenteredInsideTheirSide() {
        for (int count = 1; count <= 16; count++) {
            int columns = Math.min(4, count);
            int rowStart = (count - 1) / 4 * 4;
            int first = HologramLayout.iconX(10, rowStart, count);
            int last = HologramLayout.iconX(10, count - 1, count);
            assertEquals(2 * 10 + columns * 18 - 2, first + last + 16);
        }
    }

    @Test public void countsFromZeroToCapacityFitAndWrap() {
        for (int input = 0; input <= 16; input++) for (int output = 0; output <= 16; output++) {
            HologramLayout layout = new HologramLayout(input, output, 20);
            assertTrue(layout.width >= 32); assertTrue(layout.height >= 20);
            assertEquals(input > 0 && output > 0, layout.arrow);
            for (int i = 0; i < input; i++) assertTrue(HologramLayout.iconX(layout.inputX, i, input) + 16 <= layout.width);
            for (int i = 0; i < output; i++) assertTrue(HologramLayout.iconX(layout.outputX, i, output) + 16 <= layout.width);
        }
    }

    @Test public void singleRecipeMatchesLegacyGeometry() {
        HologramLayout legacy = new HologramLayout(5, 3, 20);
        HologramLayout banded = new HologramLayout(new int[][] {{0, 1, 2, 3, 4}}, new int[][] {{16, 17, 18}}, 20);
        assertEquals(legacy.width, banded.width); assertEquals(legacy.height, banded.height);
        assertEquals(legacy.arrowX, banded.arrowX); assertEquals(legacy.arrowY, banded.arrowY);
        assertEquals(HologramLayout.iconX(legacy.inputX, 4, 5), banded.slotX(4));
        assertEquals(HologramLayout.iconY(4), banded.slotY(4));
        assertEquals(HologramLayout.iconX(legacy.outputX, 2, 3), banded.slotX(18));
    }

    @Test public void twoRecipesAddSeparatorAndShareArrowColumn() {
        HologramLayout one = new HologramLayout(new int[][] {{0}}, new int[][] {{16}}, 20);
        HologramLayout two = new HologramLayout(new int[][] {{0}, {32, 33, 34, 35, 36}}, new int[][] {{16}, {48}}, 20);
        assertEquals(one.height + 18 * 2 + 7, two.height);
        assertEquals(2, two.bandCount());
        assertEquals(one.bandTop(0) + 18 + 7, two.bandTop(1));
        assertEquals(two.bandTop(0) + 18 + 3, two.separatorY(0));
        assertTrue(two.hasArrow(0) && two.hasArrow(1));
        assertEquals(two.slotY(32), two.bandTop(1));
        assertTrue(two.slotX(48) >= two.outputX);
    }

    @Test public void namedBandAddsHeaderAndWidensPanel() {
        HologramLayout plain = new HologramLayout(new int[][] {{0}}, new int[][] {{16}}, 20);
        HologramLayout named = new HologramLayout(new int[][] {{0}}, new int[][] {{16}}, new int[] {40}, 20);
        assertEquals(plain.height + 10, named.height);
        assertEquals(plain.slotY(0) + 10, named.slotY(0));
        assertEquals(plain.arrowY(0) + 10, named.arrowY(0));
        assertTrue(named.hasName(0)); assertFalse(plain.hasName(0));
        assertEquals(plain.bandTop(0), named.nameY(0));
        HologramLayout wide = new HologramLayout(new int[][] {{0}}, new int[][] {{16}}, new int[] {120}, 20);
        assertEquals(132, wide.width);
        HologramLayout headerOnly = new HologramLayout(new int[][] {{}}, new int[][] {{}}, new int[] {20}, 20);
        assertEquals(32, headerOnly.height);
    }

    @org.junit.Test
    public void emptyTitleDropsHeaderSpace() {
        HologramLayout titled = new HologramLayout(1, 1, 20);
        HologramLayout untitled = new HologramLayout(1, 1, 0);
        org.junit.Assert.assertEquals(titled.height - 14, untitled.height);
        org.junit.Assert.assertEquals(4, untitled.slotY(0));
        org.junit.Assert.assertEquals(titled.width, untitled.width);
    }
}
