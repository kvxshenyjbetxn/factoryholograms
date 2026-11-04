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
        HologramLayout layout = new HologramLayout(1, 1, 20);
        assertEquals(0, HologramPick.slotAt(layout, 8 + 8, 18 + 8));
        assertEquals(16, HologramPick.slotAt(layout, 46 + 8, 18 + 8));
        assertEquals(-1, HologramPick.slotAt(layout, 35, 26));
        assertEquals(0, HologramPick.slotAt(layout, 7, 17));
        assertEquals(-1, HologramPick.slotAt(layout, 6.9, 26));
    }

    @Test public void slotAtReturnsGlobalSlotInSecondBand() {
        HologramLayout layout = new HologramLayout(new int[][] {{0}, {32}}, new int[][] {{16}, {48}}, 20);
        int y = layout.slotY(48);
        assertEquals(48, HologramPick.slotAt(layout, layout.slotX(48) + 8, y + 8));
        assertEquals(32, HologramPick.slotAt(layout, layout.slotX(32) + 8, y + 8));
    }

    @Test public void pixelToWorldInvertsPanelHit() {
        float[][] angles = {{0F, 0F}, {35F, 20F}, {-120F, -40F}};
        for (float[] a : angles) {
            double[] o = HologramPick.pixelToWorld(0, 0, 3, 70, -4, a[0], a[1], S, 70, 40);
            double[] u = HologramPick.pixelToWorld(1, 0, 3, 70, -4, a[0], a[1], S, 70, 40);
            double[] v = HologramPick.pixelToWorld(0, 1, 3, 70, -4, a[0], a[1], S, 70, 40);
            double[] du = {u[0] - o[0], u[1] - o[1], u[2] - o[2]}, dv = {v[0] - o[0], v[1] - o[1], v[2] - o[2]};
            double nx = du[1] * dv[2] - du[2] * dv[1], ny = du[2] * dv[0] - du[0] * dv[2], nz = du[0] * dv[1] - du[1] * dv[0];
            double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
            nx /= len; ny /= len; nz /= len;
            double[] w = HologramPick.pixelToWorld(20, 12, 3, 70, -4, a[0], a[1], S, 70, 40);
            double[] hit = HologramPick.panelHit(w[0] - nx * 3, w[1] - ny * 3, w[2] - nz * 3, nx, ny, nz,
                3, 70, -4, a[0], a[1], S, 70, 40);
            if (hit == null) hit = HologramPick.panelHit(w[0] + nx * 3, w[1] + ny * 3, w[2] + nz * 3, -nx, -ny, -nz,
                3, 70, -4, a[0], a[1], S, 70, 40);
            assertNotNull(hit);
            assertEquals(20, hit[1], 1e-6);
            assertEquals(12, hit[2], 1e-6);
        }
    }
}
