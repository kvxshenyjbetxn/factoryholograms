package dev.factoryholograms;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

/** Pairs an output icon of one hologram with the same item as an input icon of another; pure logic, no GL. */
public final class HologramLinks {
    public static final double MAX_LINK_DISTANCE = 48.0;
    public static final int MAX_LINKS = 1024;

    private HologramLinks() {}

    public static final class Link {
        public final HologramLabel from;
        public final int fromSlot;
        public final HologramLabel to;
        public final int toSlot;

        Link(HologramLabel from, int fromSlot, HologramLabel to, int toSlot) {
            this.from = from;
            this.fromSlot = fromSlot;
            this.to = to;
            this.toSlot = toSlot;
        }
    }

    public static List<Link> compute(List<HologramLabel> labels) {
        List<Link> links = new ArrayList<Link>();
        double max2 = MAX_LINK_DISTANCE * MAX_LINK_DISTANCE;
        for (HologramLabel a : labels) {
            for (HologramLabel b : labels) {
                if (a == b) continue;
                double dx = a.x - b.x, dy = a.y - b.y, dz = a.z - b.z;
                if (dx * dx + dy * dy + dz * dz > max2) continue;
                for (int ra : a.visibleRecipes()) {
                    for (int outSlot : a.occupiedSlots(ra, true)) {
                        ItemStack out = a.getStackInSlot(outSlot);
                        for (int rb : b.visibleRecipes()) {
                            for (int inSlot : b.occupiedSlots(rb, false)) {
                                if (!sameItem(out, b.getStackInSlot(inSlot))) continue;
                                links.add(new Link(a, outSlot, b, inSlot));
                                if (links.size() >= MAX_LINKS) return links;
                            }
                        }
                    }
                }
            }
        }
        return links;
    }

    /** Same item and damage (either may be the wildcard); NBT is ignored. */
    public static boolean sameItem(ItemStack a, ItemStack b) {
        if (a == null || b == null || a.getItem() == null || a.getItem() != b.getItem()) return false;
        int da = a.getItemDamage(), db = b.getItemDamage();
        return da == db || da == OreDictionary.WILDCARD_VALUE || db == OreDictionary.WILDCARD_VALUE;
    }

    public static double distance(double[] a, double[] b) {
        double dx = a[0] - b[0], dy = a[1] - b[1], dz = a[2] - b[2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public static final double ARROW_SPACING = 0.9;
    public static final double ARROW_SPEED = 1.2;
    public static final double ARROW_END_FADE = 0.35;

    /** Ribbon half-width in blocks; grows with camera distance so the line stays visible. */
    public static double ribbonHalfWidth(double distance) {
        return Math.max(0.035, 0.0022 * distance);
    }

    /** Extra half-width of the dark outline around the ribbon. */
    public static double outlineExtra(double distance) {
        return Math.max(0.018, 0.0011 * distance);
    }

    /** Arc positions of the moving arrowheads, from start to end of a path of the given length. */
    public static double[] arrowPositions(double totalArc, double time) {
        if (totalArc <= 0) return new double[0];
        double offset = (time * ARROW_SPEED) % ARROW_SPACING;
        if (offset < 0) offset += ARROW_SPACING;
        int count = 0;
        for (double s = offset; s < totalArc; s += ARROW_SPACING) count++;
        double[] result = new double[count];
        for (int k = 0; k < count; k++) result[k] = offset + k * ARROW_SPACING;
        return result;
    }

    /** Fade factor 0..1 for an arrow at arc position s, so arrows vanish near both ends. */
    public static double arrowFade(double s, double totalArc) {
        return Math.max(0.0, Math.min(1.0, Math.min(s, totalArc - s) / ARROW_END_FADE));
    }

    /** Normalized cross(t, v), or null when degenerate. */
    public static double[] sideVector(double[] t, double[] v) {
        double x = t[1] * v[2] - t[2] * v[1];
        double y = t[2] * v[0] - t[0] * v[2];
        double z = t[0] * v[1] - t[1] * v[0];
        double len = Math.sqrt(x * x + y * y + z * z);
        if (len < 1e-6) return null;
        return new double[] {x / len, y / len, z / len};
    }

    /** Quadratic Bezier from p0 to p2; the control point is the midpoint raised in +Y. */
    public static double[] curvePoint(double[] p0, double[] p2, double t) {
        double lift = Math.max(0.3, Math.min(4.0, 0.2 * distance(p0, p2)));
        double cx = (p0[0] + p2[0]) / 2, cy = (p0[1] + p2[1]) / 2 + lift, cz = (p0[2] + p2[2]) / 2;
        double u = 1 - t;
        return new double[] {
            u * u * p0[0] + 2 * u * t * cx + t * t * p2[0],
            u * u * p0[1] + 2 * u * t * cy + t * t * p2[1],
            u * u * p0[2] + 2 * u * t * cz + t * t * p2[2]};
    }
}
