package dev.factoryholograms;

import static org.junit.Assert.*;
import org.junit.Test;

public class HologramLayoutTest {
    @Test public void oneInOneOutUsesOnlyTwoIconCells() {
        HologramLayout layout = new HologramLayout(1, 1, 0);
        assertEquals(70, layout.width); assertEquals(40, layout.height); assertTrue(layout.arrow);
        assertEquals(8, layout.inputX); assertEquals(46, layout.outputX);
    }

    @Test public void fifthItemStartsSecondRowWithoutGrowingWidth() {
        HologramLayout four = new HologramLayout(4, 1, 0);
        HologramLayout five = new HologramLayout(5, 1, 0);
        assertEquals(four.width, five.width); assertEquals(four.height + 18, five.height);
        assertEquals(five.inputX + 27, HologramLayout.iconX(five.inputX, 4, 5));
        assertEquals(36, HologramLayout.iconY(4));
        assertEquals(five.inputX + 54, HologramLayout.iconX(five.inputX, 3, 5));
        assertEquals(18, HologramLayout.iconY(3));
    }

    @Test public void emptySideDoesNotReserveCellsOrArrow() {
        HologramLayout input = new HologramLayout(1, 0, 0);
        HologramLayout output = new HologramLayout(0, 1, 0);
        assertFalse(input.arrow); assertFalse(output.arrow);
        assertEquals(32, input.width); assertEquals(input.width, output.width);
        assertEquals(8, input.inputX); assertEquals(8, output.outputX);
    }

    @Test public void titleOnlyDoesNotReserveIconRows() {
        HologramLayout layout = new HologramLayout(0, 0, 100);
        assertEquals(112, layout.width); assertEquals(20, layout.height); assertFalse(layout.arrow);
    }

    @Test public void fullInventoriesHaveFourRowsEachWithinPanel() {
        HologramLayout layout = new HologramLayout(16, 16, 0);
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
            HologramLayout layout = new HologramLayout(input, output, 0);
            assertTrue(layout.width >= 32); assertTrue(layout.height >= 20);
            assertEquals(input > 0 && output > 0, layout.arrow);
            for (int i = 0; i < input; i++) assertTrue(HologramLayout.iconX(layout.inputX, i, input) + 16 <= layout.width);
            for (int i = 0; i < output; i++) assertTrue(HologramLayout.iconX(layout.outputX, i, output) + 16 <= layout.width);
        }
    }
}
