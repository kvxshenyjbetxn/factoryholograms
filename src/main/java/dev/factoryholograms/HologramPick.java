package dev.factoryholograms;

/** Headless ray-vs-panel math; inverts the renderer's GL transform for world hologram hover. */
public final class HologramPick {
    private HologramPick() {}

    /** Returns {t, px, py} for the ray O + t*D hitting the panel, or null. */
    public static double[] panelHit(double ox, double oy, double oz, double dx, double dy, double dz,
        double cx, double cy, double cz, float yawDeg, float pitchDeg, double scale, int width, int height) {
        double[] u = rotate(-scale, 0, 0, yawDeg, pitchDeg);
        double[] v = rotate(0, -scale, 0, yawDeg, pitchDeg);
        double[] n = rotate(0, 0, 1, yawDeg, pitchDeg);
        double denominator = dx * n[0] + dy * n[1] + dz * n[2];
        if (Math.abs(denominator) < 1.0E-9D) return null;
        double t = ((cx - ox) * n[0] + (cy - oy) * n[1] + (cz - oz) * n[2]) / denominator;
        if (t <= 0.0D) return null;
        double hx = ox + t * dx - cx, hy = oy + t * dy - cy, hz = oz + t * dz - cz;
        double s2 = scale * scale;
        double px = width / 2.0D + (hx * u[0] + hy * u[1] + hz * u[2]) / s2;
        double py = height / 2.0D + (hx * v[0] + hy * v[1] + hz * v[2]) / s2;
        if (px < 0.0D || px >= width || py < 0.0D || py >= height) return null;
        return new double[] {t, px, py};
    }

    /** Index in inputs-then-outputs order of the slot under the point, or -1. */
    public static int slotAt(HologramLayout layout, int inputs, int outputs, double px, double py) {
        for (int i = 0; i < inputs; i++) {
            if (inside(HologramLayout.iconX(layout.inputX, i, inputs), HologramLayout.iconY(i), px, py)) return i;
        }
        for (int i = 0; i < outputs; i++) {
            if (inside(HologramLayout.iconX(layout.outputX, i, outputs), HologramLayout.iconY(i), px, py)) return inputs + i;
        }
        return -1;
    }

    private static boolean inside(int x, int y, double px, double py) {
        return px >= x - 1 && px < x + 17 && py >= y - 1 && py < y + 17;
    }

    /** Ry(-yaw) * Rx(pitch) applied to a vector, matching glRotatef(-yaw, y) after glRotatef(pitch, x). */
    private static double[] rotate(double x, double y, double z, float yawDeg, float pitchDeg) {
        double p = Math.toRadians(pitchDeg), pc = Math.cos(p), ps = Math.sin(p);
        double y1 = y * pc - z * ps, z1 = y * ps + z * pc;
        double w = Math.toRadians(-yawDeg), wc = Math.cos(w), ws = Math.sin(w);
        return new double[] {x * wc + z1 * ws, y1, -x * ws + z1 * wc};
    }
}
