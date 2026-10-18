package dev.factoryholograms;

/** Stable world-space anchor math and automatic front placement for new labels. */
public final class HologramPlacement {
    public static final double BASE_Y = 1.85D;

    private HologramPlacement() {}

    public static double centerX(HologramLabel label) { return label.x + 0.5D + label.getOffsetX(); }
    public static double centerY(HologramLabel label) { return label.y + BASE_Y + label.getOffsetY(); }
    public static double centerZ(HologramLabel label) { return label.z + 0.5D + LabelRules.BASE_Z + label.getOffsetZ(); }

    public static boolean sameAnchor(HologramLabel first, HologramLabel second) {
        return first != null && second != null && first.getWorld() == second.getWorld()
            && first.x == second.x && first.y == second.y && first.z == second.z;
    }

    public static int horizontalFace(int hitSide, double viewerX, double viewerZ) {
        if (hitSide >= 2 && hitSide <= 5) return hitSide;
        if (Math.abs(viewerX) > Math.abs(viewerZ)) return viewerX >= 0 ? 5 : 4;
        return viewerZ >= 0 ? 3 : 2;
    }

    public static float[] frontOffsets(int face) {
        float x = 0;
        float z = 1;
        switch (face) {
            case 2: z = -1; break;
            case 4: x = -1; z = 0; break;
            case 5: x = 1; z = 0; break;
            default: break;
        }
        return new float[] {x, 0, z - LabelRules.BASE_Z};
    }
}
