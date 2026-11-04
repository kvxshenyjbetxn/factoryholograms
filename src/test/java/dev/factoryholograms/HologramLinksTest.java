package dev.factoryholograms;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class HologramLinksTest {
    private static MinecraftTestSupport minecraft;
    @BeforeClass public static void bootstrap() throws Exception { minecraft = MinecraftTestSupport.open(HologramLinksTest.class.getName() + "$Fixture"); }
    @AfterClass public static void closeMinecraft() throws Exception { if (minecraft != null) minecraft.close(); }
    @Test public void outputMatchesInput() throws Exception { minecraft.run("outputMatchesInput"); }
    @Test public void noSelfLink() throws Exception { minecraft.run("noSelfLink"); }
    @Test public void wildcardDamageMatches() throws Exception { minecraft.run("wildcardDamageMatches"); }
    @Test public void distanceCutOff() throws Exception { minecraft.run("distanceCutOff"); }
    @Test public void multiRecipeSlots() throws Exception { minecraft.run("multiRecipeSlots"); }

    @Test public void ribbonHalfWidthGrowsWithDistance() {
        assertEquals(0.035, HologramLinks.ribbonHalfWidth(2), 1.0E-9);
        assertEquals(0.22, HologramLinks.ribbonHalfWidth(100), 1.0E-9);
    }

    @Test public void arrowPositionsSpacedAndWithinArc() {
        double[] s = HologramLinks.arrowPositions(3.0, 0.5);
        assertEquals(3, s.length);
        assertEquals(0.6, s[0], 1.0E-9);
        assertEquals(0.9, s[1] - s[0], 1.0E-9);
        assertEquals(0, HologramLinks.arrowPositions(0, 1).length);
        assertEquals(0.0, HologramLinks.arrowFade(0, 3), 0);
        assertEquals(1.0, HologramLinks.arrowFade(1.5, 3), 0);
    }

    @Test public void sideVectorPerpendicularOrNullWhenParallel() {
        double[] n = HologramLinks.sideVector(new double[] {1, 0, 0}, new double[] {0, 0, 1});
        assertArrayEquals(new double[] {0, -1, 0}, n, 1.0E-9);
        assertNull(HologramLinks.sideVector(new double[] {1, 0, 0}, new double[] {2, 0, 0}));
    }

    @Test public void curveEndpointsExactAndMidpointRaised() {
        double[] a = {0, 0, 0}, b = {10, 0, 0};
        assertArrayEquals(a, HologramLinks.curvePoint(a, b, 0), 0);
        assertArrayEquals(b, HologramLinks.curvePoint(a, b, 1), 0);
        double[] mid = HologramLinks.curvePoint(a, b, 0.5);
        assertEquals(5, mid[0], 1.0E-9);
        // control lift = 2.0, Bezier midpoint = half of it
        assertEquals(1.0, mid[1], 1.0E-9);
        double[] near = HologramLinks.curvePoint(a, new double[] {0.1, 0, 0}, 0.5);
        assertEquals(0.15, near[1], 1.0E-9);
    }

    public static final class Fixture {
        private static HologramLabel label(int x) { return new HologramLabel(null, x, 64, 0); }

        public static void outputMatchesInput() {
            HologramLabel a = label(0), b = label(5);
            a.setInventorySlotContents(LabelRules.ICONS_PER_SIDE, new ItemStack(Items.dye, 1, 1));
            b.setInventorySlotContents(2, new ItemStack(Items.dye, 1, 1));
            b.setInventorySlotContents(3, new ItemStack(Items.dye, 1, 2));
            List<HologramLabel> labels = new ArrayList<HologramLabel>();
            labels.add(a); labels.add(b);
            List<HologramLinks.Link> links = HologramLinks.compute(labels);
            assertEquals(1, links.size());
            assertSame(a, links.get(0).from); assertEquals(LabelRules.ICONS_PER_SIDE, links.get(0).fromSlot);
            assertSame(b, links.get(0).to); assertEquals(2, links.get(0).toSlot);
        }

        public static void noSelfLink() {
            HologramLabel a = label(0);
            a.setInventorySlotContents(0, new ItemStack(Items.dye, 1, 1));
            a.setInventorySlotContents(LabelRules.ICONS_PER_SIDE, new ItemStack(Items.dye, 1, 1));
            List<HologramLabel> labels = new ArrayList<HologramLabel>();
            labels.add(a);
            assertTrue(HologramLinks.compute(labels).isEmpty());
        }

        public static void wildcardDamageMatches() {
            HologramLabel a = label(0), b = label(3);
            a.setInventorySlotContents(LabelRules.ICONS_PER_SIDE, new ItemStack(Items.dye, 1, 32767));
            b.setInventorySlotContents(0, new ItemStack(Items.dye, 1, 4));
            List<HologramLabel> labels = new ArrayList<HologramLabel>();
            labels.add(a); labels.add(b);
            assertEquals(1, HologramLinks.compute(labels).size());
            assertFalse(HologramLinks.sameItem(new ItemStack(Items.dye, 1, 1), new ItemStack(Items.coal, 1, 1)));
            assertFalse(HologramLinks.sameItem(null, new ItemStack(Items.dye, 1, 1)));
        }

        public static void distanceCutOff() {
            HologramLabel a = label(0), near = label(48), far = label(49);
            a.setInventorySlotContents(LabelRules.ICONS_PER_SIDE, new ItemStack(Items.dye, 1, 1));
            near.setInventorySlotContents(0, new ItemStack(Items.dye, 1, 1));
            far.setInventorySlotContents(0, new ItemStack(Items.dye, 1, 1));
            List<HologramLabel> labels = new ArrayList<HologramLabel>();
            labels.add(a); labels.add(near); labels.add(far);
            List<HologramLinks.Link> links = HologramLinks.compute(labels);
            assertEquals(1, links.size());
            assertSame(near, links.get(0).to);
        }

        public static void multiRecipeSlots() {
            HologramLabel a = label(0), b = label(4);
            a.addRecipe();
            int out = LabelRules.slot(1, LabelRules.ICONS_PER_SIDE);
            int in = LabelRules.slot(1, 1);
            a.setInventorySlotContents(out, new ItemStack(Items.dye, 1, 7));
            b.setInventorySlotContents(0, new ItemStack(Items.dye, 1, 7));
            b.addRecipe();
            b.setInventorySlotContents(in, new ItemStack(Items.dye, 1, 7));
            List<HologramLabel> labels = new ArrayList<HologramLabel>();
            labels.add(a); labels.add(b);
            List<HologramLinks.Link> links = HologramLinks.compute(labels);
            assertEquals(2, links.size());
            assertEquals(out, links.get(0).fromSlot); assertEquals(0, links.get(0).toSlot);
            assertEquals(out, links.get(1).fromSlot); assertEquals(in, links.get(1).toSlot);
        }
    }
}
