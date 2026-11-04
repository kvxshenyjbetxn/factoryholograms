package dev.factoryholograms.client;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.world.World;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import dev.factoryholograms.HologramConfig;
import dev.factoryholograms.HologramLabel;
import dev.factoryholograms.LabelRules;
import dev.factoryholograms.HologramLinks;
import dev.factoryholograms.HologramLinks.Link;

/** Animated curved ribbons with moving arrowheads from an output icon to the matching input icon of another hologram. */
@SideOnly(Side.CLIENT)
public final class HologramLinkRenderer {
    private static final double MAX_SEGMENT = 0.15D;

    private final HologramRenderer renderer;
    // Per-frame caches, cleared on every render call.
    private final Map<HologramLabel, Boolean> usable = new IdentityHashMap<HologramLabel, Boolean>();
    private final Map<HologramLabel, Map<Integer, double[]>> positions = new IdentityHashMap<HologramLabel, Map<Integer, double[]>>();

    public HologramLinkRenderer(HologramRenderer renderer) {
        this.renderer = renderer;
    }

    public void render(List<Link> links, double camX, double camY, double camZ, float partialTicks,
        HologramLabel focus, int focusSlot, int focusRecipe, float focusAlpha) {
        Minecraft mc = Minecraft.getMinecraft();
        World world = mc.theWorld;
        if (links.isEmpty() || world == null || HologramConfig.LINK_OFF.equals(HologramConfig.linkMode)) return;
        boolean focusMode = !HologramConfig.LINK_ALL.equals(HologramConfig.linkMode);
        if (focusMode && (focus == null || focusAlpha <= 0.0F)) return;
        usable.clear();
        positions.clear();
        double range = HologramConfig.renderDistance + 16.0D;
        float alpha = Math.max(0.25F, Math.min(1.0F, HologramConfig.opacity)) * (focusMode ? focusAlpha : 1.0F);
        float red = ((HologramConfig.linkColor >> 16) & 255) / 255.0F;
        float green = ((HologramConfig.linkColor >> 8) & 255) / 255.0F;
        float blue = (HologramConfig.linkColor & 255) / 255.0F;
        double time = (world.getTotalWorldTime() + partialTicks) / 20.0D;
        boolean walls = HologramConfig.linksThroughWalls;

        int previousMatrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_FOG);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDepthMask(false);
            for (Link link : links) {
                if (focusMode && !selected(link, focus, focusSlot, focusRecipe)) continue;
                if (!usable(world, link.from) || !usable(world, link.to)) continue;
                if (!near(link.from, camX, camY, camZ, range) && !near(link.to, camX, camY, camZ, range)) continue;
                double[] a = position(link.from, link.fromSlot);
                double[] b = position(link.to, link.toSlot);
                if (a == null || b == null) continue;
                double[] from = {a[0] - camX, a[1] - camY, a[2] - camZ};
                double[] to = {b[0] - camX, b[1] - camY, b[2] - camZ};
                Path path = new Path(from, to);
                double[] arrows = HologramLinks.arrowPositions(path.total, time);
                if (walls) {
                    GL11.glDisable(GL11.GL_DEPTH_TEST);
                    drawLink(path, arrows, red, green, blue, alpha * 0.30F);
                }
                GL11.glEnable(GL11.GL_DEPTH_TEST);
                GL11.glDepthFunc(GL11.GL_LEQUAL);
                drawLink(path, arrows, red, green, blue, alpha);
            }
        } finally {
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();
            GL11.glPopAttrib();
            GL11.glMatrixMode(previousMatrixMode);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    private static boolean selected(Link link, HologramLabel focus, int focusSlot, int focusRecipe) {
        if (link.from == focus) return matches(link.fromSlot, focusSlot, focusRecipe);
        if (link.to == focus) return matches(link.toSlot, focusSlot, focusRecipe);
        return false;
    }

    private static boolean matches(int slot, int focusSlot, int focusRecipe) {
        if (focusSlot >= 0) return slot == focusSlot;
        return focusRecipe < 0 || LabelRules.recipeOf(slot) == focusRecipe;
    }

    private static boolean near(HologramLabel label, double x, double y, double z, double range) {
        double dx = label.x - x, dy = label.y - y, dz = label.z - z;
        return dx * dx + dy * dy + dz * dz <= range * range;
    }

    private boolean usable(World world, HologramLabel label) {
        Boolean cached = usable.get(label);
        if (cached == null) {
            cached = label.getWorld() == world && ClientProxy.isChunkLoaded(world, label.x >> 4, label.z >> 4) && label.matchesBlock();
            usable.put(label, cached);
        }
        return cached;
    }

    private double[] position(HologramLabel label, int slot) {
        Map<Integer, double[]> byLabel = positions.get(label);
        if (byLabel == null) {
            byLabel = new HashMap<Integer, double[]>();
            positions.put(label, byLabel);
        }
        if (byLabel.containsKey(slot)) return byLabel.get(slot);
        double[] position = renderer.slotWorldPosition(label, slot);
        byLabel.put(slot, position);
        return position;
    }

    private static double[] normalize(double[] v) {
        double len = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        if (len < 1e-9) return new double[] {0, 1, 0};
        return new double[] {v[0] / len, v[1] / len, v[2] / len};
    }

    private static double length(double[] v) {
        return Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
    }

    /** Sampled curve with tangents, ribbon side vectors, half-widths and cumulative arc lengths. */
    private static final class Path {
        double[][] points;
        double[][] tangents;
        double[][] sides;
        double[] halfWidths;
        double[] arcs;
        double total;

        Path(double[] from, double[] to) {
            double chord = HologramLinks.distance(from, to);
            int segments = (int) Math.max(8, Math.min(256, Math.ceil(chord * 1.1D / MAX_SEGMENT)));
            int n = segments + 1;
            points = new double[n][];
            tangents = new double[n][];
            sides = new double[n][];
            halfWidths = new double[n];
            arcs = new double[n];
            for (int i = 0; i < n; i++) {
                points[i] = HologramLinks.curvePoint(from, to, i / (double) segments);
                if (i > 0) arcs[i] = arcs[i - 1] + HologramLinks.distance(points[i - 1], points[i]);
            }
            total = arcs[n - 1];
            double[] previousSide = {0, 1, 0};
            for (int i = 0; i < n; i++) {
                double[] a = points[Math.max(0, i - 1)];
                double[] b = points[Math.min(n - 1, i + 1)];
                double[] t = normalize(new double[] {b[0] - a[0], b[1] - a[1], b[2] - a[2]});
                double[] side = HologramLinks.sideVector(t, normalize(points[i]));
                if (side == null) side = previousSide;
                previousSide = side;
                tangents[i] = t;
                sides[i] = side;
                halfWidths[i] = HologramLinks.ribbonHalfWidth(length(points[i]));
            }
        }

        void ribbon(float r, float g, float b, float alpha, boolean outline) {
            Tessellator tessellator = Tessellator.instance;
            tessellator.startDrawing(GL11.GL_TRIANGLE_STRIP);
            tessellator.setColorRGBA_F(r, g, b, alpha);
            for (int i = 0; i < points.length; i++) {
                double[] p = points[i];
                double hw = halfWidths[i];
                if (outline) hw += HologramLinks.outlineExtra(length(p));
                double[] n = sides[i];
                tessellator.addVertex(p[0] + n[0] * hw, p[1] + n[1] * hw, p[2] + n[2] * hw);
                tessellator.addVertex(p[0] - n[0] * hw, p[1] - n[1] * hw, p[2] - n[2] * hw);
            }
            tessellator.draw();
        }

        void arrows(double[] positions, float r, float g, float b, float alpha, double scale) {
            Tessellator tessellator = Tessellator.instance;
            tessellator.startDrawing(GL11.GL_TRIANGLES);
            int last = points.length - 1;
            int index = 0;
            for (double s : positions) {
                while (index < last - 1 && arcs[index + 1] < s) index++;
                double span = arcs[index + 1] - arcs[index];
                double f = span > 1e-9 ? Math.max(0.0, Math.min(1.0, (s - arcs[index]) / span)) : 0.0;
                double[] p = new double[3];
                for (int k = 0; k < 3; k++) p[k] = points[index][k] + (points[index + 1][k] - points[index][k]) * f;
                double[] t = normalize(new double[] {
                    tangents[index][0] + (tangents[index + 1][0] - tangents[index][0]) * f,
                    tangents[index][1] + (tangents[index + 1][1] - tangents[index][1]) * f,
                    tangents[index][2] + (tangents[index + 1][2] - tangents[index][2]) * f});
                double[] n = sides[f < 0.5 ? index : index + 1];
                double hw = HologramLinks.ribbonHalfWidth(length(p));
                double len = 4.5 * hw * scale;
                double halfW = 2.4 * hw * scale;
                double fade = HologramLinks.arrowFade(s, total);
                if (fade <= 0.0) continue;
                tessellator.setColorRGBA_F(r, g, b, alpha * (float) fade);
                tessellator.addVertex(p[0] + t[0] * len * 0.6, p[1] + t[1] * len * 0.6, p[2] + t[2] * len * 0.6);
                tessellator.addVertex(p[0] - t[0] * len * 0.4 + n[0] * halfW, p[1] - t[1] * len * 0.4 + n[1] * halfW,
                    p[2] - t[2] * len * 0.4 + n[2] * halfW);
                tessellator.addVertex(p[0] - t[0] * len * 0.4 - n[0] * halfW, p[1] - t[1] * len * 0.4 - n[1] * halfW,
                    p[2] - t[2] * len * 0.4 - n[2] * halfW);
            }
            tessellator.draw();
        }
    }

    private static void drawLink(Path path, double[] arrows, float red, float green, float blue, float alpha) {
        path.ribbon(0.0F, 0.0F, 0.0F, 0.55F * alpha, true);
        path.ribbon(red, green, blue, 0.90F * alpha, false);
        path.arrows(arrows, 0.0F, 0.0F, 0.0F, 0.6F * alpha, 1.3D);
        path.arrows(arrows, red + (1.0F - red) * 0.35F, green + (1.0F - green) * 0.35F,
            blue + (1.0F - blue) * 0.35F, 0.95F * alpha, 1.0D);
    }
}
