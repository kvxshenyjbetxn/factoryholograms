package dev.factoryholograms;

import static org.junit.Assert.*;

import net.minecraft.init.Items;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class HologramLabelTest {
    private static MinecraftTestSupport minecraft;
    @BeforeClass public static void bootstrap() throws Exception { minecraft = MinecraftTestSupport.open(HologramLabelTest.class.getName() + "$Fixture"); }
    @AfterClass public static void closeMinecraft() throws Exception { if (minecraft != null) minecraft.close(); }
    @Test public void defaultsAndLabelBounds() throws Exception { minecraft.run("defaultsAndLabelBounds"); }
    @Test public void ghostCopiesStackAndNestedNbt() throws Exception { minecraft.run("ghostCopiesStackAndNestedNbt"); }
    @Test public void noExtractionAndExplicitClear() throws Exception { minecraft.run("noExtractionAndExplicitClear"); }
    @Test public void invalidInventorySlotsAreIgnored() throws Exception { minecraft.run("invalidInventorySlotsAreIgnored"); }
    @Test public void nbtRoundTripPreservesCoordinatesIdentityAndIcons() throws Exception { minecraft.run("nbtRoundTripPreservesCoordinatesIdentityAndIcons"); }
    @Test public void missingAndUntrustedDataAreSanitized() throws Exception { minecraft.run("missingAndUntrustedDataAreSanitized"); }
    @Test public void copyFromKeepsCachedObjectAndDeepCopiesIcons() throws Exception { minecraft.run("copyFromKeepsCachedObjectAndDeepCopiesIcons"); }
    @Test public void oversizedGhostCannotReplaceExistingIcon() throws Exception { minecraft.run("oversizedGhostCannotReplaceExistingIcon"); }

    @Test public void positionPersistsAndCopiesWithoutMovingAnchor() throws Exception { minecraft.run("positionPersistsAndCopiesWithoutMovingAnchor"); }
    @Test public void oldAndInvalidSavedPositionsAreSafe() throws Exception { minecraft.run("oldAndInvalidSavedPositionsAreSafe"); }

    @Test public void legacyIconsAndPositionMigrateExactlyOnce() throws Exception { minecraft.run("legacyIconsAndPositionMigrateExactlyOnce"); }
    @Test public void allThirtyTwoIconsPersistAndStayWithinBudget() throws Exception { minecraft.run("allThirtyTwoIconsPersistAndStayWithinBudget"); }

    @Test public void localLabelSurvivesMetadataChangesWithoutEditingWorld() throws Exception { minecraft.run("localLabelSurvivesMetadataChangesWithoutEditingWorld"); }
    @Test public void registryNamesOverrideObsoleteNumericItemIds() throws Exception { minecraft.run("registryNamesOverrideObsoleteNumericItemIds"); }

    @Test public void rotationPersistsAndCopies() throws Exception { minecraft.run("rotationPersistsAndCopies"); }
    @Test public void missingAndInvalidRotationsAreSafe() throws Exception { minecraft.run("missingAndInvalidRotationsAreSafe"); }

    public static final class Fixture {
        public static void rotationPersistsAndCopies() {
            HologramLabel label = new HologramLabel(null, 1, 64, 2);
            label.setRotation(-135, 45);
            label.setLabel("Rotated", 1, 2, 3);
            HologramLabel restored = HologramLabel.fromNBT(null, label.toNBT());
            HologramLabel cached = new HologramLabel(null, 1, 64, 2);
            cached.copyFrom(restored);
            assertEquals(-135, cached.getYaw(), 0);
            assertEquals(45, cached.getPitch(), 0);
            assertEquals(1, cached.getOffsetX(), 0);
            restored.setRotation(0, 0);
            assertEquals(-135, cached.getYaw(), 0);
        }
        public static void missingAndInvalidRotationsAreSafe() {
            NBTTagCompound tag = new NBTTagCompound();
            HologramLabel label = HologramLabel.fromNBT(null, tag);
            assertEquals(0, label.getYaw(), 0);
            assertEquals(0, label.getPitch(), 0);
            for (float value : new float[] {Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
                tag.setFloat("Yaw", value); tag.setFloat("Pitch", value);
                label = HologramLabel.fromNBT(null, tag);
                assertEquals(0, label.getYaw(), 0);
                assertEquals(0, label.getPitch(), 0);
            }
            label.setRotation(1000, -1000);
            assertEquals(180, label.getYaw(), 0);
            assertEquals(-90, label.getPitch(), 0);
            tag.setFloat("Yaw", -1000); tag.setFloat("Pitch", 1000);
            label = HologramLabel.fromNBT(null, tag);
            assertEquals(-180, label.getYaw(), 0);
            assertEquals(90, label.getPitch(), 0);
        }
        public static void localLabelSurvivesMetadataChangesWithoutEditingWorld() {
            LabelTestSupport.TestWorld world = new LabelTestSupport.TestWorld();
            HologramLabel label = new HologramLabel(world, 0, 64, 0);
            assertTrue(label.isEmpty()); label.setLabel("EBF"); assertFalse(label.isEmpty());
            world.block(0, 64, 0, Blocks.stone, 3); assertTrue(label.matchesBlock());
            world.loaded = false; assertFalse(label.matchesBlock());
            world.loaded = true; assertTrue(label.matchesBlock());
            world.block(0, 64, 0, Blocks.dirt, 0); assertFalse(label.matchesBlock());
            assertEquals("EBF", label.getTitle());
        }
        public static void registryNamesOverrideObsoleteNumericItemIds() {
            HologramLabel label = new HologramLabel(null, 0, 64, 0);
            label.setInventorySlotContents(0, new ItemStack(Items.iron_ingot, 64));
            NBTTagCompound saved = label.toNBT();
            NBTTagCompound icon = saved.getTagList("Icons", 10).getCompoundTagAt(0);
            assertEquals("minecraft:iron_ingot", icon.getString("RegistryName"));
            icon.setShort("id", (short) net.minecraft.item.Item.getIdFromItem(Items.diamond));
            assertSame(Items.iron_ingot, HologramLabel.fromNBT(null, saved).getStackInSlot(0).getItem());
        }
        public static void legacyIconsAndPositionMigrateExactlyOnce() {
            NBTTagCompound tag = new NBTTagCompound(); tag.setFloat("OffsetZ", 1.0F);
            NBTTagList icons = new NBTTagList();
            for (int i = 0; i < 6; i++) {
                NBTTagCompound entry = new NBTTagCompound();
                new ItemStack(Items.dye, 1, i).writeToNBT(entry); entry.setByte("Slot", (byte) i); icons.appendTag(entry);
            }
            tag.setTag("Icons", icons);
            HologramLabel migrated = HologramLabel.fromNBT(null, tag);
            assertEquals(0, migrated.getOffsetZ(), 0);
            for (int i = 0; i < 3; i++) {
                assertEquals(i, migrated.getStackInSlot(i).getItemDamage());
                assertEquals(i + 3, migrated.getStackInSlot(LabelRules.ICONS_PER_SIDE + i).getItemDamage());
            }
            HologramLabel again = HologramLabel.fromNBT(null, migrated.toNBT());
            assertEquals(0, again.getOffsetZ(), 0);
            assertArrayEquals(new int[] {16, 17, 18}, again.occupiedSlots(true));
            HologramLabel fresh = new HologramLabel(null, 0, 64, 0);
            assertEquals(0, fresh.getOffsetX(), 0); assertEquals(0, fresh.getOffsetY(), 0); assertEquals(0, fresh.getOffsetZ(), 0);
            assertEquals(1.0F, LabelRules.BASE_Z + fresh.getOffsetZ(), 0);
        }
        public static void allThirtyTwoIconsPersistAndStayWithinBudget() {
            HologramLabel label = new HologramLabel(null, 0, 64, 0);
            for (int i = 0; i < LabelRules.ICON_SLOTS; i++) label.setInventorySlotContents(i, stack(64));
            HologramLabel restored = HologramLabel.fromNBT(null, label.toNBT());
            assertEquals(16, restored.occupiedSlots(false).length); assertEquals(16, restored.occupiedSlots(true).length);
            for (int i = 0; i < LabelRules.ICON_SLOTS; i++) assertEquals(1, restored.getStackInSlot(i).stackSize);
            HologramLabel large = new HologramLabel(null, 0, 64, 0);
            ItemStack heavy = stack(64); heavy.getTagCompound().setByteArray("data", new byte[800]);
            for (int i = 0; i < LabelRules.ICON_SLOTS; i++) large.setInventorySlotContents(i, heavy);
            int count = large.occupiedSlots(false).length + large.occupiedSlots(true).length;
            assertTrue(count >= 6); assertTrue(count < LabelRules.ICON_SLOTS);
            assertEquals(count, large.toNBT().getTagList("Icons", 10).tagCount());
            large.setInventorySlotContents(0, null); large.setInventorySlotContents(31, heavy); assertNotNull(large.getStackInSlot(31));
        }
        public static void positionPersistsAndCopiesWithoutMovingAnchor() {
            HologramLabel label = new HologramLabel(null, 10, 64, -7);
            label.setLabel("EBF", -2.25F, 5.5F, 1.75F);
            label.setLabel("Renamed");
            HologramLabel restored = HologramLabel.fromNBT(null, label.toNBT());
            HologramLabel cached = new HologramLabel(null, 10, 64, -7);
            cached.copyFrom(restored);
            assertEquals(10, cached.x); assertEquals(64, cached.y); assertEquals(-7, cached.z);
            assertEquals("Renamed", cached.getTitle());
            assertEquals(-2.25F, cached.getOffsetX(), 0);
            assertEquals(5.5F, cached.getOffsetY(), 0);
            assertEquals(1.75F, cached.getOffsetZ(), 0);
        }
        public static void oldAndInvalidSavedPositionsAreSafe() {
            NBTTagCompound tag = new NBTTagCompound();
            HologramLabel old = HologramLabel.fromNBT(null, tag);
            assertEquals(0, old.getOffsetX(), 0); assertEquals(0, old.getOffsetY(), 0); assertEquals(-1, old.getOffsetZ(), 0);
            tag.setFloat("OffsetX", Float.NaN); tag.setFloat("OffsetY", Float.POSITIVE_INFINITY); tag.setFloat("OffsetZ", -100);
            HologramLabel invalid = HologramLabel.fromNBT(null, tag);
            assertEquals(0, invalid.getOffsetX(), 0); assertEquals(0, invalid.getOffsetY(), 0); assertEquals(-32, invalid.getOffsetZ(), 0);
        }
        public static void defaultsAndLabelBounds() {
            HologramLabel label = new HologramLabel(null, 0, 64, 0);
            assertEquals("", label.getTitle()); assertEquals(LabelRules.ICON_SLOTS, label.getSizeInventory());
            assertEquals(1, label.getInventoryStackLimit()); assertFalse(label.isUseableByPlayer(null));
            label.setLabel(" \u00a7Вхід\n "); assertEquals("Вхід", label.getTitle());
            label.setLabel(null); assertEquals("", label.getTitle());
        }
        public static void ghostCopiesStackAndNestedNbt() {
            HologramLabel label = new HologramLabel(null, 0, 64, 0);
            ItemStack source = stack(48); label.setInventorySlotContents(0, source);
            ItemStack ghost = label.getStackInSlot(0);
            assertNotSame(source, ghost); assertNotSame(source.getTagCompound(), ghost.getTagCompound());
            assertSame(Items.dye, ghost.getItem()); assertEquals(4, ghost.getItemDamage());
            assertEquals(1, ghost.stackSize); assertEquals(48, source.stackSize);
            source.getTagCompound().getCompoundTag("nested").setString("name", "changed");
            source.setItemDamage(2); source.stackSize = 3;
            assertEquals("original", ghost.getTagCompound().getCompoundTag("nested").getString("name"));
            assertEquals(4, ghost.getItemDamage()); assertEquals(1, ghost.stackSize);
        }
        public static void noExtractionAndExplicitClear() {
            HologramLabel label = new HologramLabel(null, 0, 64, 0);
            for (int slot = 0; slot < LabelRules.ICON_SLOTS; slot++) {
                label.setInventorySlotContents(slot, stack(64)); ItemStack ghost = label.getStackInSlot(slot);
                for (int count : new int[] {-1, 0, 1, 64, Integer.MAX_VALUE}) {
                    assertNull(label.decrStackSize(slot, count)); assertSame(ghost, label.getStackInSlot(slot));
                }
                assertNull(label.getStackInSlotOnClosing(slot)); assertFalse(label.isItemValidForSlot(slot, stack(1)));
                label.setInventorySlotContents(slot, null); assertNull(label.getStackInSlot(slot));
            }
        }
        public static void invalidInventorySlotsAreIgnored() {
            HologramLabel label = new HologramLabel(null, 0, 64, 0);
            label.setInventorySlotContents(0, stack(1)); ItemStack ghost = label.getStackInSlot(0);
            for (int slot : new int[] {Integer.MIN_VALUE, -1, LabelRules.ICON_SLOTS, 255, Integer.MAX_VALUE}) {
                assertNull(label.getStackInSlot(slot)); label.setInventorySlotContents(slot, stack(64));
                label.setInventorySlotContents(slot, null); assertNull(label.decrStackSize(slot, 1));
                assertNull(label.getStackInSlotOnClosing(slot));
            }
            assertSame(ghost, label.getStackInSlot(0));
        }
        public static void nbtRoundTripPreservesCoordinatesIdentityAndIcons() {
            LabelTestSupport.TestWorld world = new LabelTestSupport.TestWorld();
            HologramLabel original = new HologramLabel(world, -123, 72, 456);
            original.setLabel("Завод → 鉄"); original.setInventorySlotContents(0, stack(48));
            original.setInventorySlotContents(5, new ItemStack(Items.iron_ingot, 64));
            NBTTagCompound tag = original.toNBT(); assertEquals("minecraft:stone", tag.getString("Block"));
            assertEquals(0, tag.getInteger("Meta")); assertFalse(tag.hasKey("Radius"));
            HologramLabel restored = HologramLabel.fromNBT(world, tag);
            assertSame(world, restored.getWorld()); assertEquals(-123, restored.x); assertEquals(72, restored.y); assertEquals(456, restored.z);
            assertEquals(original.getTitle(), restored.getTitle()); assertTrue(restored.matchesBlock());
            assertTrue(ItemStack.areItemStacksEqual(original.getStackInSlot(0), restored.getStackInSlot(0)));
            restored.getStackInSlot(0).getTagCompound().getCompoundTag("nested").setString("name", "loaded");
            assertEquals("original", original.getStackInSlot(0).getTagCompound().getCompoundTag("nested").getString("name"));
            assertSame(Items.iron_ingot, restored.getStackInSlot(5).getItem()); assertEquals(1, restored.getStackInSlot(5).stackSize);
        }
        public static void missingAndUntrustedDataAreSanitized() {
            NBTTagCompound tag = new NBTTagCompound(); tag.setString("Title", " \u00a7A\nB\u007f ");
            NBTTagList icons = new NBTTagList();
            for (int slot : new int[] {0, 5, -1, 6, 127, 128}) {
                NBTTagCompound entry = new NBTTagCompound(); stack(64).writeToNBT(entry); entry.setByte("Slot", (byte) slot); icons.appendTag(entry);
            }
            tag.setTag("Icons", icons); HologramLabel label = HologramLabel.fromNBT(null, tag);
            assertEquals("AB", label.getTitle()); assertEquals(1, label.getStackInSlot(0).stackSize);
            assertEquals(1, label.getStackInSlot(LabelRules.ICONS_PER_SIDE + 2).stackSize);
            for (int slot = 1; slot < 5; slot++) assertNull(label.getStackInSlot(slot));
            label.copyFrom(HologramLabel.fromNBT(null, new NBTTagCompound()));
            assertEquals("", label.getTitle()); for (int slot = 0; slot < LabelRules.ICON_SLOTS; slot++) assertNull(label.getStackInSlot(slot));
        }
        public static void copyFromKeepsCachedObjectAndDeepCopiesIcons() {
            HologramLabel cached = new HologramLabel(null, 1, 64, 2);
            HologramLabel update = new HologramLabel(null, 1, 64, 2);
            update.setLabel("updated"); update.setInventorySlotContents(3, stack(64));
            HologramLabel same = cached; cached.copyFrom(update);
            assertSame(same, cached); assertEquals("updated", cached.getTitle());
            assertNotSame(update.getStackInSlot(3), cached.getStackInSlot(3));
            assertNotSame(update.getStackInSlot(3).getTagCompound(), cached.getStackInSlot(3).getTagCompound());
            assertThrows(IllegalArgumentException.class, () -> cached.copyFrom(new HologramLabel(null, 2, 64, 2)));
        }
        public static void oversizedGhostCannotReplaceExistingIcon() {
            HologramLabel label = new HologramLabel(null, 0, 64, 0); label.setInventorySlotContents(0, stack(1));
            ItemStack previous = label.getStackInSlot(0); ItemStack large = stack(32);
            large.getTagCompound().setByteArray("large", new byte[LabelRules.MAX_ICON_BYTES]);
            label.setInventorySlotContents(0, large); assertSame(previous, label.getStackInSlot(0)); assertEquals(32, large.stackSize);
        }
        private static ItemStack stack(int count) {
            ItemStack stack = new ItemStack(Items.dye, count, 4); NBTTagCompound tag = new NBTTagCompound();
            NBTTagCompound nested = new NBTTagCompound(); nested.setString("name", "original");
            tag.setTag("nested", nested); stack.setTagCompound(tag); return stack;
        }
    }
}
