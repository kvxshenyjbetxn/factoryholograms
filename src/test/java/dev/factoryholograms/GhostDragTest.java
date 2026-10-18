package dev.factoryholograms;

import static org.junit.Assert.*;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class GhostDragTest {
    private static MinecraftTestSupport minecraft;
    @BeforeClass public static void bootstrap() throws Exception { minecraft = MinecraftTestSupport.open(GhostDragTest.class.getName() + "$Fixture"); }
    @AfterClass public static void closeMinecraft() throws Exception { if (minecraft != null) minecraft.close(); }
    @Test public void beginsWithIndependentCountOneCopy() throws Exception { minecraft.run("beginsWithIndependentCountOneCopy"); }
    @Test public void sourceAndPreviewMutationsCannotChangeDrag() throws Exception { minecraft.run("sourceAndPreviewMutationsCannotChangeDrag"); }
    @Test public void copyingIntoGhostDoesNotConsumeSourceOrPreview() throws Exception { minecraft.run("copyingIntoGhostDoesNotConsumeSourceOrPreview"); }
    @Test public void cancelOrNullSourceLeavesNoCarriedItem() throws Exception { minecraft.run("cancelOrNullSourceLeavesNoCarriedItem"); }
    @Test public void pointerJitterIsClickAndMovementStaysRecorded() throws Exception { minecraft.run("pointerJitterIsClickAndMovementStaysRecorded"); }
    @Test public void newGestureResetsMovementAndNegativeCoordinatesWork() throws Exception { minecraft.run("newGestureResetsMovementAndNegativeCoordinatesWork"); }

    public static final class Fixture {
        private static ItemStack source() {
            ItemStack stack = new ItemStack(Items.dye, 37, 4);
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString("name", "original"); stack.setTagCompound(tag);
            return stack;
        }
        public static void beginsWithIndependentCountOneCopy() {
            ItemStack source = source(); GhostDrag drag = new GhostDrag();
            assertFalse(drag.active()); assertNull(drag.stack());
            drag.begin(source, 10, 20);
            ItemStack preview = drag.stack();
            assertTrue(drag.active()); assertNotSame(source, preview);
            assertNotSame(source.getTagCompound(), preview.getTagCompound());
            assertEquals(1, preview.stackSize); assertEquals(4, preview.getItemDamage());
            assertEquals(37, source.stackSize);
        }
        public static void sourceAndPreviewMutationsCannotChangeDrag() {
            ItemStack source = source(); GhostDrag drag = new GhostDrag(); drag.begin(source, 0, 0);
            source.setItemDamage(2); source.getTagCompound().setString("name", "source changed");
            ItemStack preview = drag.stack(); preview.stackSize = 0; preview.getTagCompound().setString("name", "preview changed");
            assertEquals(4, drag.stack().getItemDamage()); assertEquals(1, drag.stack().stackSize);
            assertEquals("original", drag.stack().getTagCompound().getString("name"));
        }
        public static void copyingIntoGhostDoesNotConsumeSourceOrPreview() {
            ItemStack source = source(); GhostDrag drag = new GhostDrag(); drag.begin(source, 0, 0);
            HologramLabel draft = new HologramLabel(null, 0, 64, 0);
            ItemStack preview = drag.stack(); draft.setInventorySlotContents(0, preview); drag.clear();
            assertFalse(drag.active()); assertEquals(37, source.stackSize); assertEquals(1, preview.stackSize);
            assertEquals(1, draft.getStackInSlot(0).stackSize);
            assertNotSame(preview, draft.getStackInSlot(0));
        }
        public static void cancelOrNullSourceLeavesNoCarriedItem() {
            ItemStack source = source(); GhostDrag drag = new GhostDrag();
            drag.begin(source, 0, 0); drag.clear();
            assertFalse(drag.active()); assertNull(drag.stack()); assertFalse(drag.movedTo(100, 100));
            drag.begin(source, 0, 0); drag.begin(null, 10, 10);
            assertFalse(drag.active()); assertNull(drag.stack()); assertEquals(37, source.stackSize);
        }
        public static void pointerJitterIsClickAndMovementStaysRecorded() {
            GhostDrag drag = new GhostDrag(); drag.begin(source(), 10, 20);
            assertFalse(drag.movedTo(12, 22)); assertFalse(drag.movedTo(13, 20));
            assertTrue(drag.movedTo(13, 23)); assertTrue(drag.movedTo(10, 20));
        }
        public static void newGestureResetsMovementAndNegativeCoordinatesWork() {
            GhostDrag drag = new GhostDrag(); drag.begin(source(), -10, -20);
            assertTrue(drag.movedTo(-50, -20)); drag.begin(source(), -50, -20);
            assertFalse(drag.movedTo(-50, -20));
            assertTrue(drag.movedTo(Integer.MAX_VALUE, Integer.MIN_VALUE));
        }
    }
}
