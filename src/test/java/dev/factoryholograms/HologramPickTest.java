package dev.factoryholograms;

import static org.junit.Assert.*;
import org.junit.Test;

public class HologramPickTest {
    private static final double S = 0.0125D;

    @Test public void rayAtCenterHitsPanelCenter() {
        // yaw 0: panel normal is +z, so a ray along +z from -z side is parallel-facing and hits.
        double[] hit = HologramPick.panelHit(0, 0, -5, 0, 0, 1, 0, 0, 0, 0F, 0F, S, 70, 40);
        assertNotNull(hit);
        assertEquals(5.0, hit[0], 1e-9); assertEquals(35.0, hit[1], 1e-9); assertEquals(20.0, hit[2], 1e-9);
    }

    @Test public void pixelXRunsOppositeToWorldXAtYawZero() {
        double[] hit = HologramPick.panelHit(0.125, 0, -5, 0, 0, 1, 0, 0, 0, 0F, 0F, S, 70, 40);
        assertNotNull(hit);
        assertEquals(25.0, hit[1], 1e-9);
        double[] up = HologramPick.panelHit(0, 0.125, -5, 0, 0, 1, 0, 0, 0, 0F, 0F, S, 70, 40);
        assertEquals(10.0, up[2], 1e-9);
    }

    @Test public void yaw90RotatesPanelNormalToXAxis() {
        double[] hit = HologramPick.panelHit(5, 0, 0, -1, 0, 0, 0, 0, 0, 90F, 0F, S, 70, 40);
        assertNotNull(hit);
        assertEquals(35.0, hit[1], 1e-9); assertEquals(20.0, hit[2], 1e-9);
        // Offset along world z maps to pixel x with the rotated U axis.
        double[] side = HologramPick.panelHit(5, 0, 0.125, -1, 0, 0, 0, 0, 0, 90F, 0F, S, 70, 40);
        assertNotNull(side);
        assertEquals(25.0, side[1], 1e-9);
    }

    @Test public void missesOutsidePanelBehindAndParallel() {
        assertNull(HologramPick.panelHit(2, 0, -5, 0, 0, 1, 0, 0, 0, 0F, 0F, S, 70, 40));
        assertNull(HologramPick.panelHit(0, 0, 5, 0, 0, 1, 0, 0, 0, 0F, 0F, S, 70, 40));
        assertNull(HologramPick.panelHit(0, 0, -5, 1, 0, 0, 0, 0, 0, 0F, 0F, S, 70, 40));
    }

    @Test public void slotAtFindsInputOutputAndGap() {
        HologramLayout layout = new HologramLayout(1, 1, 0);
        assertEquals(0, HologramPick.slotAt(layout, 1, 1, 8 + 8, 18 + 8));
        assertEquals(1, HologramPick.slotAt(layout, 1, 1, 46 + 8, 18 + 8));
        assertEquals(-1, HologramPick.slotAt(layout, 1, 1, 35, 26));
        assertEquals(0, HologramPick.slotAt(layout, 1, 1, 7, 17));
        assertEquals(-1, HologramPick.slotAt(layout, 1, 1, 6.9, 26));
    }
}
