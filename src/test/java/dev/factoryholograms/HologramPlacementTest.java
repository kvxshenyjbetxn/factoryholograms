package dev.factoryholograms;

import static org.junit.Assert.*;


import org.junit.Test;

public class HologramPlacementTest {
    @Test public void defaultCoordinatesStayAtEstablishedWorldCenter() {
        HologramLabel label = new HologramLabel(null, -12, 64, 30);
        assertEquals(0, label.getOffsetX(), 0); assertEquals(0, label.getOffsetY(), 0); assertEquals(0, label.getOffsetZ(), 0);
        assertEquals(-11.5, HologramPlacement.centerX(label), 0.000001);
        assertEquals(65.85, HologramPlacement.centerY(label), 0.000001);
        assertEquals(31.5, HologramPlacement.centerZ(label), 0.000001);
        assertEquals(1.0F, LabelRules.BASE_Z, 0);
    }

    @Test public void explicitOffsetsMoveCenterExactlyWithoutChangingAnchor() {
        HologramLabel label = new HologramLabel(null, 10, 64, -8);
        label.setLabel("EBF", 0.25F, 2.5F, -3.75F);
        assertEquals(10.75, HologramPlacement.centerX(label), 0.000001);
        assertEquals(68.35, HologramPlacement.centerY(label), 0.000001);
        assertEquals(-10.25, HologramPlacement.centerZ(label), 0.000001);
        assertEquals(10, label.x); assertEquals(64, label.y); assertEquals(-8, label.z);
    }

    @Test public void frontFormulaPlacesEachHorizontalFaceOutsideTheBlock() {
        int[] faces = {2, 3, 4, 5};
        double[][] expected = {{0.5, -0.5}, {0.5, 1.5}, {-0.5, 0.5}, {1.5, 0.5}};
        for (int i = 0; i < faces.length; i++) {
            HologramLabel label = new HologramLabel(null, 0, 64, 0);
            float[] offsets = HologramPlacement.frontOffsets(faces[i]);
            label.setLabel("EBF", offsets[0], offsets[1], offsets[2]);
            assertEquals(expected[i][0], HologramPlacement.centerX(label), 0.000001);
            assertEquals(expected[i][1], HologramPlacement.centerZ(label), 0.000001);
            assertEquals(65.85, HologramPlacement.centerY(label), 0.000001);
        }
    }

    @Test public void topAndBottomHitsChooseHorizontalDirectionTowardViewer() {
        assertEquals(5, HologramPlacement.horizontalFace(1, 3, 1));
        assertEquals(4, HologramPlacement.horizontalFace(0, -3, 1));
        assertEquals(2, HologramPlacement.horizontalFace(1, 1, -3));
        assertEquals(3, HologramPlacement.horizontalFace(0, 1, 3));
        for (int side = 2; side <= 5; side++) assertEquals(side, HologramPlacement.horizontalFace(side, 10, 10));
    }

    @Test public void separateDraftReplacesOnlyItsMatchingAnchor() {
        HologramLabel saved = new HologramLabel(null, 10, 64, 20);
        HologramLabel preview = new HologramLabel(null, 10, 64, 20);
        preview.setLabel("preview", 5, 0, -2);
        assertTrue(HologramPlacement.sameAnchor(saved, preview));
        assertFalse(HologramPlacement.sameAnchor(saved, new HologramLabel(null, 11, 64, 20)));
        assertFalse(HologramPlacement.sameAnchor(saved, null));
        assertEquals(0, saved.getOffsetX(), 0);
    }

    @Test public void frontPresetsDisplayZeroAndSurviveEditingReloadAndReset() {
        for (int face = 2; face <= 5; face++) {
            HologramLabel label = new HologramLabel(null, 0, 64, 0);
            label.setPlacementFace(face);
            float[] base = HologramPlacement.frontOffsets(face);
            label.setLabel("EBF", base[0], base[1], base[2]);
            assertArrayEquals(new float[] {0, 0, 0}, label.getPositionOffsets(), 0);
            double x = HologramPlacement.centerX(label);
            double z = HologramPlacement.centerZ(label);
            label.setLabel("EBF", base[0] + 0.25F, 0.5F, base[2] - 0.75F);
            label = HologramLabel.fromNBT(null, label.toNBT());
            assertEquals(face, label.getPlacementFace());
            assertArrayEquals(new float[] {0.25F, 0.5F, -0.75F}, label.getPositionOffsets(), 0);
            HologramLabel copy = new HologramLabel(null, 0, 64, 0);
            copy.copyFrom(label);
            assertEquals(face, copy.getPlacementFace());
            copy.setLabel("EBF", base[0], base[1], base[2]);
            assertArrayEquals(new float[] {0, 0, 0}, copy.getPositionOffsets(), 0);
            assertEquals(x, HologramPlacement.centerX(copy), 0);
            assertEquals(z, HologramPlacement.centerZ(copy), 0);
        }
    }

    @Test public void oldAutomaticPresetsBecomeZeroWithoutMovingInWorld() {
        for (int face = 2; face <= 5; face++) {
            HologramLabel old = new HologramLabel(null, 10, 64, 20);
            float[] base = HologramPlacement.frontOffsets(face);
            old.setLabel("old", base[0], base[1], base[2]);
            net.minecraft.nbt.NBTTagCompound tag = old.toNBT();
            tag.removeTag("PlacementFace");
            HologramLabel migrated = HologramLabel.fromNBT(null, tag);
            assertArrayEquals(new float[] {0, 0, 0}, migrated.getPositionOffsets(), 0);
            assertEquals(HologramPlacement.centerX(old), HologramPlacement.centerX(migrated), 0);
            assertEquals(HologramPlacement.centerZ(old), HologramPlacement.centerZ(migrated), 0);
        }
    }
}
