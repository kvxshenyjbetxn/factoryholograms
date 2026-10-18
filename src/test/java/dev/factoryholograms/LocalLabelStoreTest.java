package dev.factoryholograms;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;

import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.zip.GZIPOutputStream;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class LocalLabelStoreTest {
    private static MinecraftTestSupport minecraft;
    @BeforeClass public static void bootstrap() throws Exception { minecraft = MinecraftTestSupport.open(LocalLabelStoreTest.class.getName() + "$Fixture"); }
    @AfterClass public static void closeMinecraft() throws Exception { if (minecraft != null) minecraft.close(); }
    @Test public void saveRemoveAndBlankPutRoundTrip() throws Exception { minecraft.run("saveRemoveAndBlankPutRoundTrip"); }
    @Test public void draftsAndSnapshotsAreIndependentAndCancelDoesNotSave() throws Exception { minecraft.run("draftsAndSnapshotsAreIndependentAndCancelDoesNotSave"); }
    @Test public void worldIdentityAndCoordinatesAreValidated() throws Exception { minecraft.run("worldIdentityAndCoordinatesAreValidated"); }
    @Test public void serverDimensionAndSingleplayerFilesAreIsolated() throws Exception { minecraft.run("serverDimensionAndSingleplayerFilesAreIsolated"); }
    @Test public void diskFailuresDoNotCommitPutOrRemove() throws Exception { minecraft.run("diskFailuresDoNotCommitPutOrRemove"); }
    @Test public void backupAlwaysContainsPreviousSuccessfulVersion() throws Exception { minecraft.run("backupAlwaysContainsPreviousSuccessfulVersion"); }
    @Test public void corruptOrFutureFilesAreNotOverwritten() throws Exception { minecraft.run("corruptOrFutureFilesAreNotOverwritten"); }
    @Test public void corruptionAfterOpeningPreservesFileMemoryAndBackup() throws Exception { minecraft.run("corruptionAfterOpeningPreservesFileMemoryAndBackup"); }
    @Test public void gzipLegacyMigrationCopiesWithoutChangingSource() throws Exception { minecraft.run("gzipLegacyMigrationCopiesWithoutChangingSource"); }
    @Test public void loadedLabelsDoNotValidateBlocksOrForceChunks() throws Exception { minecraft.run("loadedLabelsDoNotValidateBlocksOrForceChunks"); }
    @Test public void chunkAndTotalCapsApplyToLoadAndPut() throws Exception { minecraft.run("chunkAndTotalCapsApplyToLoadAndPut"); }
    @Test public void malformedCoordinatesListsAndDuplicatesAreRejected() throws Exception { minecraft.run("malformedCoordinatesListsAndDuplicatesAreRejected"); }
    @Test public void fileAndInflatedGzipBytesAreBounded() throws Exception { minecraft.run("fileAndInflatedGzipBytesAreBounded"); }
    @Test public void iconCapsApplyEvenToMutatedInventoryStacks() throws Exception { minecraft.run("iconCapsApplyEvenToMutatedInventoryStacks"); }

    @Test public void livePositionDraftDoesNotWriteUntilApply() throws Exception { minecraft.run("livePositionDraftDoesNotWriteUntilApply"); }

    @Test public void newEditorDraftAutomaticallyFacesTheTargetedSide() throws Exception { minecraft.run("newEditorDraftAutomaticallyFacesTheTargetedSide"); }
    @Test public void reopeningFromAnotherSidePreservesSavedPosition() throws Exception { minecraft.run("reopeningFromAnotherSidePreservesSavedPosition"); }
    @Test public void replacementGetsAutomaticDraftWithoutOverwritingSavedData() throws Exception { minecraft.run("replacementGetsAutomaticDraftWithoutOverwritingSavedData"); }
    @Test public void unloadedSavedAnchorIsNeverRepositioned() throws Exception { minecraft.run("unloadedSavedAnchorIsNeverRepositioned"); }

    public static final class Fixture {
        public static void newEditorDraftAutomaticallyFacesTheTargetedSide() throws Exception {
            try (Directory directory = new Directory()) {
                LabelTestSupport.TestWorld world = new LabelTestSupport.TestWorld();
                File file = directory.path.resolve("DIM0.dat").toFile();
                LocalLabelStore store = new LocalLabelStore(file, world);
                double[][] expected = {{0.5, -0.5}, {0.5, 1.5}, {-0.5, 0.5}, {1.5, 0.5}};
                for (int face = 2; face <= 5; face++) {
                    HologramLabel draft = store.draftForEditing(0, 64, 0, face);
                    assertEquals(expected[face - 2][0], HologramPlacement.centerX(draft), 0.000001);
                    assertEquals(expected[face - 2][1], HologramPlacement.centerZ(draft), 0.000001);
                    assertEquals(65.85, HologramPlacement.centerY(draft), 0.000001);
                    assertTrue(draft.isEmpty()); assertTrue(store.labels().isEmpty()); assertFalse(file.exists());
                }
            }
        }
        public static void reopeningFromAnotherSidePreservesSavedPosition() throws Exception {
            try (Directory directory = new Directory()) {
                LabelTestSupport.TestWorld world = new LabelTestSupport.TestWorld();
                File file = directory.path.resolve("DIM0.dat").toFile();
                LocalLabelStore store = new LocalLabelStore(file, world);
                HologramLabel saved = store.draftForEditing(0, 64, 0, 2);
                saved.setLabel("custom", 4.25F, -1.5F, 7.75F);
                saved.setInventorySlotContents(0, icon()); store.put(saved);
                byte[] before = Files.readAllBytes(file.toPath());
                for (int face = 2; face <= 5; face++) {
                    HologramLabel reopened = store.draftForEditing(0, 64, 0, face);
                    assertEquals("custom", reopened.getTitle());
                    assertEquals(4.25F, reopened.getOffsetX(), 0);
                    assertEquals(-1.5F, reopened.getOffsetY(), 0);
                    assertEquals(7.75F, reopened.getOffsetZ(), 0);
                    assertNotNull(reopened.getStackInSlot(0));
                    assertArrayEquals(before, Files.readAllBytes(file.toPath()));
                }
            }
        }
        public static void replacementGetsAutomaticDraftWithoutOverwritingSavedData() throws Exception {
            try (Directory directory = new Directory()) {
                LabelTestSupport.TestWorld world = new LabelTestSupport.TestWorld();
                File file = directory.path.resolve("DIM0.dat").toFile();
                LocalLabelStore store = new LocalLabelStore(file, world);
                HologramLabel saved = store.draft(0, 64, 0); saved.setLabel("old", 5, 6, 7); store.put(saved);
                byte[] before = Files.readAllBytes(file.toPath());
                world.block(0, 64, 0, Blocks.dirt, 0);
                HologramLabel replacement = store.draftForEditing(0, 64, 0, 5);
                assertTrue(replacement.isEmpty()); assertTrue(replacement.matchesBlock());
                assertEquals(1.5, HologramPlacement.centerX(replacement), 0.000001);
                assertEquals(0.5, HologramPlacement.centerZ(replacement), 0.000001);
                assertEquals("old", store.draft(0, 64, 0).getTitle());
                assertArrayEquals(before, Files.readAllBytes(file.toPath()));
            }
        }
        public static void unloadedSavedAnchorIsNeverRepositioned() throws Exception {
            try (Directory directory = new Directory()) {
                LabelTestSupport.TestWorld world = new LabelTestSupport.TestWorld();
                LocalLabelStore store = new LocalLabelStore(directory.path.resolve("DIM0.dat").toFile(), world);
                HologramLabel saved = store.draft(0, 64, 0); saved.setLabel("keep", 1, 2, 3); store.put(saved);
                world.loaded = false;
                HologramLabel reopened = store.draftForEditing(0, 64, 0, 4);
                assertEquals("keep", reopened.getTitle());
                assertEquals(1, reopened.getOffsetX(), 0); assertEquals(2, reopened.getOffsetY(), 0); assertEquals(3, reopened.getOffsetZ(), 0);
            }
        }
        public static void livePositionDraftDoesNotWriteUntilApply() throws Exception {
            try (Directory directory = new Directory()) {
                LabelTestSupport.TestWorld world = new LabelTestSupport.TestWorld();
                File file = directory.path.resolve("DIM0.dat").toFile();
                LocalLabelStore store = new LocalLabelStore(file, world);
                HologramLabel saved = store.draft(0, 64, 0); saved.setLabel("EBF"); store.put(saved);
                byte[] previous = Files.readAllBytes(file.toPath());
                HologramLabel preview = store.draft(0, 64, 0);
                for (int i = 1; i <= 8; i++) {
                    preview.setLabel("Preview", i * 0.25F, 2, -2);
                    assertEquals(0.5D + i * 0.25D, HologramPlacement.centerX(preview), 0.000001);
                    assertEquals(0, store.draft(0, 64, 0).getOffsetX(), 0);
                    assertArrayEquals(previous, Files.readAllBytes(file.toPath()));
                }
                assertEquals("EBF", store.draft(0, 64, 0).getTitle());
                assertFalse(backup(file).exists());
                store.put(preview);
                HologramLabel restored = new LocalLabelStore(file, world).draft(0, 64, 0);
                assertEquals("Preview", restored.getTitle()); assertEquals(2, restored.getOffsetX(), 0);
                assertEquals(-2, restored.getOffsetZ(), 0);
                assertArrayEquals(previous, Files.readAllBytes(backup(file).toPath()));
            }
        }
        public static void saveRemoveAndBlankPutRoundTrip() throws Exception {
            try (Directory directory = new Directory()) {
                LabelTestSupport.TestWorld world = new LabelTestSupport.TestWorld();
                File file = directory.path.resolve("nested/DIM0.dat").toFile();
                LocalLabelStore store = new LocalLabelStore(file, world);
                assertEquals(file, store.getFile()); assertFalse(file.exists());
                HologramLabel first = store.draft(-1, 64, -17);
                first.setLabel("Factory → 鉄", -2.25F, 3.5F, 1.75F);
                first.setInventorySlotContents(31, icon());
                assertTrue(store.put(first)); assertFalse(store.put(first));
                HologramLabel second = store.draft(32, 255, 48); second.setLabel("second"); store.put(second);
                LocalLabelStore loaded = new LocalLabelStore(file, world);
                assertEquals(2, loaded.labels().size());
                HologramLabel copy = loaded.draft(-1, 64, -17);
                assertSame(world, copy.getWorld()); assertEquals(first.getTitle(), copy.getTitle());
                assertEquals(-2.25F, copy.getOffsetX(), 0); assertEquals(3.5F, copy.getOffsetY(), 0); assertEquals(1.75F, copy.getOffsetZ(), 0);
                assertTrue(ItemStack.areItemStacksEqual(first.getStackInSlot(31), copy.getStackInSlot(31)));
                copy.setLabel(""); copy.setInventorySlotContents(31, null);
                assertTrue(loaded.put(copy)); assertEquals(1, new LocalLabelStore(file, world).labels().size());
                loaded.remove(32, 255, 48);
                assertTrue(new LocalLabelStore(file, world).labels().isEmpty());
                byte[] empty = Files.readAllBytes(file.toPath());
                loaded.remove(32, 255, 48); assertFalse(loaded.put(copy));
                assertArrayEquals(empty, Files.readAllBytes(file.toPath()));
                NBTTagCompound raw = LabelNbt.decode(empty);
                assertTrue(raw.hasKey("Labels", 9)); assertEquals(0, raw.getTagList("Labels", 10).tagCount());
            }
        }

        public static void draftsAndSnapshotsAreIndependentAndCancelDoesNotSave() throws Exception {
            try (Directory directory = new Directory()) {
                File file = directory.path.resolve("DIM0.dat").toFile();
                LabelTestSupport.TestWorld world = new LabelTestSupport.TestWorld();
                LocalLabelStore store = new LocalLabelStore(file, world);
                HologramLabel canceled = store.draft(0, 64, 0); canceled.setLabel("canceled");
                canceled.setInventorySlotContents(0, icon());
                assertFalse(file.exists()); assertTrue(store.labels().isEmpty()); assertTrue(store.draft(0, 64, 0).isEmpty());
                store.put(canceled);
                byte[] committed = Files.readAllBytes(file.toPath());
                canceled.setLabel("after put"); canceled.getStackInSlot(0).getTagCompound().setString("name", "mutated");
                HologramLabel a = store.draft(0, 64, 0), b = store.draft(0, 64, 0);
                assertNotSame(a, b); assertNotSame(a.getStackInSlot(0), b.getStackInSlot(0));
                a.setLabel("cancel again"); a.getStackInSlot(0).getTagCompound().setString("name", "changed");
                assertEquals("canceled", b.getTitle()); assertEquals("original", b.getStackInSlot(0).getTagCompound().getString("name"));
                Collection<HologramLabel> snapshot = store.labels();
                assertThrows(UnsupportedOperationException.class, () -> snapshot.clear());
                snapshot.iterator().next().setLabel("snapshot edit");
                assertEquals("canceled", store.draft(0, 64, 0).getTitle());
                assertArrayEquals(committed, Files.readAllBytes(file.toPath()));
                assertFalse(backup(file).exists());
            }
        }

        public static void worldIdentityAndCoordinatesAreValidated() throws Exception {
            try (Directory directory = new Directory()) {
                File file = directory.path.resolve("DIM0.dat").toFile();
                LabelTestSupport.TestWorld world = new LabelTestSupport.TestWorld();
                LocalLabelStore store = new LocalLabelStore(file, world);
                HologramLabel foreign = new HologramLabel(new LabelTestSupport.TestWorld(), 0, 64, 0); foreign.setLabel("foreign");
                assertThrows(IllegalArgumentException.class, () -> store.put(foreign));
                assertThrows(IllegalArgumentException.class, () -> store.put(null));
                int[][] invalid = {{0, -1, 0}, {0, 256, 0}, {-30000001, 64, 0}, {30000001, 64, 0}, {0, 64, -30000001}, {0, 64, 30000001}};
                for (int[] pos : invalid) {
                    assertThrows(IllegalArgumentException.class, () -> store.draft(pos[0], pos[1], pos[2]));
                    assertThrows(IllegalArgumentException.class, () -> store.remove(pos[0], pos[1], pos[2]));
                    HologramLabel label = new HologramLabel(world, pos[0], pos[1], pos[2]); label.setLabel("bad");
                    assertThrows(IllegalArgumentException.class, () -> store.put(label));
                }
                assertFalse(file.exists()); assertTrue(store.labels().isEmpty());
                for (int[] pos : new int[][] {{-30000000, 0, 30000000}, {30000000, 255, -30000000}}) {
                    HologramLabel edge = store.draft(pos[0], pos[1], pos[2]); edge.setLabel("edge"); store.put(edge);
                }
                assertEquals(2, new LocalLabelStore(file, world).labels().size());
            }
        }

        public static void serverDimensionAndSingleplayerFilesAreIsolated() throws Exception {
            try (Directory directory = new Directory()) {
                LabelTestSupport.TestWorld world = new LabelTestSupport.TestWorld();
                File base = directory.path.toFile();
                File[] files = {LocalWorldKey.dataFile(base, false, "one.invalid", 0),
                    LocalWorldKey.dataFile(base, false, "one.invalid", -1),
                    LocalWorldKey.dataFile(base, false, "two.invalid", 0),
                    LocalWorldKey.dataFile(base, true, "one.invalid", 0)};
                for (int i = 0; i < files.length; i++) {
                    LocalLabelStore store = new LocalLabelStore(files[i], world);
                    assertTrue(store.labels().isEmpty());
                    HologramLabel label = store.draft(0, 64, 0); label.setLabel("file " + i); store.put(label);
                }
                for (int i = 0; i < files.length; i++) assertEquals("file " + i, new LocalLabelStore(files[i], world).draft(0, 64, 0).getTitle());
            }
        }

        public static void diskFailuresDoNotCommitPutOrRemove() throws Exception {
            try (Directory directory = new Directory()) {
                LabelTestSupport.TestWorld world = new LabelTestSupport.TestWorld();
                Path blocked = directory.path.resolve("not-a-directory");
                File inaccessible = blocked.resolve("DIM0.dat").toFile();
                LocalLabelStore empty = new LocalLabelStore(inaccessible, world);
                Files.write(blocked, new byte[] {1});
                HologramLabel fresh = empty.draft(0, 64, 0); fresh.setLabel("not committed");
                assertThrows(IOException.class, () -> empty.put(fresh)); assertTrue(empty.labels().isEmpty());
                File file = directory.path.resolve("DIM0.dat").toFile();
                LocalLabelStore store = new LocalLabelStore(file, world);
                HologramLabel label = store.draft(0, 64, 0); label.setLabel("saved"); store.put(label);
                byte[] saved = Files.readAllBytes(file.toPath());
                Path backup = backup(file).toPath(); Files.createDirectory(backup); Files.write(backup.resolve("blocker"), new byte[] {2});
                label.setLabel("failed");
                IOException failure = assertThrows(IOException.class, () -> store.put(label));
                assertTrue(failure.getMessage().contains(file.toString())); assertTrue(failure.getMessage().contains("Memory was not committed"));
                assertEquals("saved", store.draft(0, 64, 0).getTitle()); assertArrayEquals(saved, Files.readAllBytes(file.toPath()));
                assertThrows(IOException.class, () -> store.remove(0, 64, 0));
                assertEquals(1, store.labels().size()); assertArrayEquals(saved, Files.readAllBytes(file.toPath()));
                assertArrayEquals(new byte[] {2}, Files.readAllBytes(backup.resolve("blocker")));
                Files.delete(backup.resolve("blocker")); Files.delete(backup);
                store.put(label); assertEquals("failed", new LocalLabelStore(file, world).draft(0, 64, 0).getTitle());
            }
        }

        public static void backupAlwaysContainsPreviousSuccessfulVersion() throws Exception {
            try (Directory directory = new Directory()) {
                File file = directory.path.resolve("DIM0.dat").toFile();
                LabelTestSupport.TestWorld world = new LabelTestSupport.TestWorld();
                LocalLabelStore store = new LocalLabelStore(file, world);
                HologramLabel label = store.draft(0, 64, 0); label.setLabel("first"); store.put(label);
                byte[] first = Files.readAllBytes(file.toPath()); assertFalse(backup(file).exists());
                label.setLabel("second"); store.put(label);
                assertArrayEquals(first, Files.readAllBytes(backup(file).toPath()));
                assertEquals("first", new LocalLabelStore(backup(file), world).draft(0, 64, 0).getTitle());
                byte[] second = Files.readAllBytes(file.toPath());
                store.remove(0, 64, 0);
                assertArrayEquals(second, Files.readAllBytes(backup(file).toPath()));
                assertTrue(new LocalLabelStore(file, world).labels().isEmpty());
                assertEquals(2, directory.path.toFile().list().length);
            }
        }

        public static void corruptOrFutureFilesAreNotOverwritten() throws Exception {
            try (Directory directory = new Directory()) {
                Path file = directory.path.resolve("DIM0.dat");
                LabelTestSupport.TestWorld world = new LabelTestSupport.TestWorld();
                corrupt(file, world, new byte[] {1, 2, 3});
                corrupt(file, world, new byte[0]);
                corrupt(file, world, LabelNbt.encode(new NBTTagCompound(), LocalLabelStore.MAX_FILE_BYTES));
                NBTTagCompound future = root(); future.setInteger("Format", 2);
                corrupt(file, world, LabelNbt.encode(future, LocalLabelStore.MAX_FILE_BYTES));
                future.setString("Format", "1"); corrupt(file, world, LabelNbt.encode(future, LocalLabelStore.MAX_FILE_BYTES));
                NBTTagCompound label = label(world, 0, 64, 0); label.setInteger("LabelFormat", 3);
                corrupt(file, world, LabelNbt.encode(root(label), LocalLabelStore.MAX_FILE_BYTES));
                byte[] valid = LabelNbt.encode(root(), LocalLabelStore.MAX_FILE_BYTES);
                byte[] trailing = new byte[valid.length + 1]; System.arraycopy(valid, 0, trailing, 0, valid.length);
                corrupt(file, world, trailing);
                assertFalse(backup(file.toFile()).exists());
            }
        }

        public static void corruptionAfterOpeningPreservesFileMemoryAndBackup() throws Exception {
            try (Directory directory = new Directory()) {
                File file = directory.path.resolve("DIM0.dat").toFile();
                LabelTestSupport.TestWorld world = new LabelTestSupport.TestWorld();
                LocalLabelStore store = new LocalLabelStore(file, world);
                HologramLabel label = store.draft(0, 64, 0); label.setLabel("first"); store.put(label);
                label.setLabel("second"); store.put(label);
                byte[] goodBackup = Files.readAllBytes(backup(file).toPath());
                byte[] broken = {1, 2, 3}; Files.write(file.toPath(), broken);
                label.setLabel("third"); assertThrows(IOException.class, () -> store.put(label));
                assertThrows(IOException.class, () -> store.remove(0, 64, 0));
                assertArrayEquals(broken, Files.readAllBytes(file.toPath()));
                assertArrayEquals(goodBackup, Files.readAllBytes(backup(file).toPath()));
                assertEquals("second", store.draft(0, 64, 0).getTitle());
            }
        }

        public static void gzipLegacyMigrationCopiesWithoutChangingSource() throws Exception {
            try (Directory directory = new Directory()) {
                LabelTestSupport.TestWorld world = new LabelTestSupport.TestWorld();
                for (boolean wrapped : new boolean[] {false, true}) {
                    Path legacyDirectory = directory.path.resolve(wrapped ? "wrapped" : "direct"); Files.createDirectory(legacyDirectory);
                    Path source = legacyDirectory.resolve("factoryholograms_labels.dat");
                    NBTTagCompound label = label(world, -1, 64, -17); label.removeTag("LabelFormat"); label.setFloat("OffsetZ", 1.25F);
                    NBTTagList icons = new NBTTagList();
                    for (int slot : new int[] {0, 3, 5}) {
                        NBTTagCompound entry = new NBTTagCompound(); icon().writeToNBT(entry); entry.setByte("Slot", (byte) slot); icons.appendTag(entry);
                    }
                    label.setTag("Icons", icons);
                    NBTTagCompound legacy = root(label);
                    if (wrapped) { NBTTagCompound wrapper = new NBTTagCompound(); wrapper.setTag("data", legacy); legacy = wrapper; }
                    try (OutputStream output = Files.newOutputStream(source)) { CompressedStreamTools.writeCompressed(legacy, output); }
                    byte[] original = Files.readAllBytes(source);
                    File target = LocalWorldKey.dataFile(directory.path.toFile(), true, wrapped ? "World2" : "World1", -1);
                    Files.createDirectories(target.toPath().getParent()); Files.copy(source, target.toPath());
                    LocalLabelStore store = new LocalLabelStore(target, world);
                    HologramLabel migrated = store.draft(-1, 64, -17);
                    assertEquals(0.25F, migrated.getOffsetZ(), 0);
                    assertNotNull(migrated.getStackInSlot(0)); assertNotNull(migrated.getStackInSlot(16)); assertNotNull(migrated.getStackInSlot(18));
                    assertArrayEquals(original, Files.readAllBytes(target.toPath())); assertFalse(backup(target).exists());
                    migrated.setLabel("intentionally changed"); store.put(migrated);
                    assertArrayEquals(original, Files.readAllBytes(source));
                    assertArrayEquals(original, Files.readAllBytes(backup(target).toPath()));
                    assertEquals(10, Files.readAllBytes(target.toPath())[0]);
                    HologramLabel reread = new LocalLabelStore(target, world).draft(-1, 64, -17);
                    assertEquals("intentionally changed", reread.getTitle()); assertEquals(0.25F, reread.getOffsetZ(), 0);
                }
            }
        }

        public static void loadedLabelsDoNotValidateBlocksOrForceChunks() throws Exception {
            try (Directory directory = new Directory()) {
                File file = directory.path.resolve("DIM0.dat").toFile();
                LabelTestSupport.TestWorld world = new LabelTestSupport.TestWorld();
                LocalLabelStore store = new LocalLabelStore(file, world);
                HologramLabel label = store.draft(0, 64, 0); label.setLabel("keep"); store.put(label);
                world.block(0, 64, 0, Blocks.dirt, 1); world.loaded = false;
                LocalLabelStore loaded = new LocalLabelStore(file, world);
                HologramLabel draft = loaded.draft(0, 64, 0);
                assertEquals("keep", draft.getTitle()); assertEquals("minecraft:stone", draft.toNBT().getString("Block"));
                assertFalse(draft.matchesBlock()); assertEquals(1, loaded.labels().size());
                assertNotNull(loaded.draft(160, 64, 0));
                world.loaded = true;
                assertFalse(loaded.draft(0, 64, 0).matchesBlock());
                assertEquals("keep", loaded.draft(0, 64, 0).getTitle());
            }
        }

        public static void chunkAndTotalCapsApplyToLoadAndPut() throws Exception {
            try (Directory directory = new Directory()) {
                Path path = directory.path.resolve("DIM0.dat");
                LabelTestSupport.TestWorld world = new LabelTestSupport.TestWorld();
                NBTTagCompound fullChunk = manyLabels(world, LabelRules.MAX_LABELS_PER_CHUNK, true); write(path, fullChunk);
                LocalLabelStore chunk = new LocalLabelStore(path.toFile(), world);
                assertEquals(64, chunk.labels().size());
                HologramLabel extra = chunk.draft(-16, 65, -16); extra.setLabel("65th");
                byte[] original = Files.readAllBytes(path);
                assertThrows(IOException.class, () -> chunk.put(extra)); assertEquals(64, chunk.labels().size());
                assertArrayEquals(original, Files.readAllBytes(path));
                HologramLabel replacement = chunk.draft(-16, 64, -16); replacement.setLabel("replacement"); chunk.put(replacement);
                HologramLabel otherChunk = chunk.draft(0, 64, 0); otherChunk.setLabel("other chunk"); chunk.put(otherChunk);
                assertEquals(65, chunk.labels().size());
                corrupt(path, world, LabelNbt.encode(manyLabels(world, 65, true), LocalLabelStore.MAX_FILE_BYTES));
                write(path, manyLabels(world, LocalLabelStore.MAX_LABELS, false));
                LocalLabelStore all = new LocalLabelStore(path.toFile(), world);
                assertEquals(4096, all.labels().size());
                HologramLabel over = all.draft(4096 * 16, 64, 0); over.setLabel("4097th");
                original = Files.readAllBytes(path);
                assertThrows(IOException.class, () -> all.put(over)); assertEquals(4096, all.labels().size());
                assertArrayEquals(original, Files.readAllBytes(path));
                corrupt(path, world, LabelNbt.encode(manyLabels(world, LocalLabelStore.MAX_LABELS + 1, false), LocalLabelStore.MAX_FILE_BYTES));
            }
        }

        public static void malformedCoordinatesListsAndDuplicatesAreRejected() throws Exception {
            try (Directory directory = new Directory()) {
                Path path = directory.path.resolve("DIM0.dat"); LabelTestSupport.TestWorld world = new LabelTestSupport.TestWorld();
                int[][] positions = {{0, -1, 0}, {0, 256, 0}, {-30000001, 64, 0}, {30000001, 64, 0}, {0, 64, -30000001}, {0, 64, 30000001}};
                for (int[] pos : positions) corrupt(path, world, LabelNbt.encode(root(label(world, pos[0], pos[1], pos[2])), LocalLabelStore.MAX_FILE_BYTES));
                NBTTagCompound missing = label(world, 0, 64, 0); missing.removeTag("y");
                corrupt(path, world, LabelNbt.encode(root(missing), LocalLabelStore.MAX_FILE_BYTES));
                missing.setString("y", "64"); corrupt(path, world, LabelNbt.encode(root(missing), LocalLabelStore.MAX_FILE_BYTES));
                NBTTagCompound valid = label(world, 0, 64, 0);
                corrupt(path, world, LabelNbt.encode(root(valid, (NBTTagCompound) valid.copy()), LocalLabelStore.MAX_FILE_BYTES));
                NBTTagCompound wrong = root(); wrong.setString("Labels", "not a list");
                corrupt(path, world, LabelNbt.encode(wrong, LocalLabelStore.MAX_FILE_BYTES));
                NBTTagList strings = new NBTTagList(); strings.appendTag(new NBTTagString("not a compound")); wrong.setTag("Labels", strings);
                corrupt(path, world, LabelNbt.encode(wrong, LocalLabelStore.MAX_FILE_BYTES));
                valid.setString("Icons", "not a list"); corrupt(path, world, LabelNbt.encode(root(valid), LocalLabelStore.MAX_FILE_BYTES));
            }
        }

        public static void fileAndInflatedGzipBytesAreBounded() throws Exception {
            try (Directory directory = new Directory()) {
                Path path = directory.path.resolve("DIM0.dat"); LabelTestSupport.TestWorld world = new LabelTestSupport.TestWorld();
                try (RandomAccessFile large = new RandomAccessFile(path.toFile(), "rw")) { large.setLength(LocalLabelStore.MAX_FILE_BYTES + 1L); }
                assertThrows(IOException.class, () -> new LocalLabelStore(path.toFile(), world));
                assertEquals(LocalLabelStore.MAX_FILE_BYTES + 1L, Files.size(path));
                try (GZIPOutputStream gzip = new GZIPOutputStream(Files.newOutputStream(path))) {
                    byte[] block = new byte[8192];
                    for (int written = 0; written < LocalLabelStore.MAX_FILE_BYTES; written += block.length) gzip.write(block);
                    gzip.write(0);
                }
                byte[] bomb = Files.readAllBytes(path); assertTrue(bomb.length < LocalLabelStore.MAX_FILE_BYTES);
                IOException failure = assertThrows(IOException.class, () -> new LocalLabelStore(path.toFile(), world));
                assertTrue(failure.getMessage().contains("exceeds")); assertArrayEquals(bomb, Files.readAllBytes(path));
                corrupt(path, world, new byte[] {0x1f, (byte) 0x8b, 1, 2, 3});
            }
        }

        public static void iconCapsApplyEvenToMutatedInventoryStacks() throws Exception {
            try (Directory directory = new Directory()) {
                Path path = directory.path.resolve("DIM0.dat"); LabelTestSupport.TestWorld world = new LabelTestSupport.TestWorld();
                LocalLabelStore store = new LocalLabelStore(path.toFile(), world);
                HologramLabel label = store.draft(0, 64, 0); label.setLabel("all slots");
                for (int i = 0; i < LabelRules.ICON_SLOTS; i++) label.setInventorySlotContents(i, icon());
                store.put(label);
                HologramLabel loaded = new LocalLabelStore(path.toFile(), world).draft(0, 64, 0);
                assertEquals(16, loaded.occupiedSlots(false).length); assertEquals(16, loaded.occupiedSlots(true).length);
                byte[] good = Files.readAllBytes(path);
                HologramLabel oversized = store.draft(0, 64, 0);
                oversized.getStackInSlot(0).getTagCompound().setByteArray("oversized", new byte[LabelRules.MAX_ICON_BYTES]);
                assertThrows(IOException.class, () -> store.put(oversized)); assertArrayEquals(good, Files.readAllBytes(path));
                NBTTagCompound tooLarge = oversized.toNBT();
                corrupt(path, world, LabelNbt.encode(root(tooLarge), LocalLabelStore.MAX_FILE_BYTES));
                HologramLabel aggregate = new HologramLabel(world, 0, 64, 0); aggregate.setLabel("aggregate");
                for (int i = 0; i < 8; i++) aggregate.setInventorySlotContents(i, icon());
                for (int i = 0; i < 8; i++) aggregate.getStackInSlot(i).getTagCompound().setByteArray("payload", new byte[800]);
                Files.write(path, good);
                assertThrows(IOException.class, () -> store.put(aggregate)); assertArrayEquals(good, Files.readAllBytes(path));
                assertEquals("all slots", store.draft(0, 64, 0).getTitle());
                corrupt(path, world, LabelNbt.encode(root(aggregate.toNBT()), LocalLabelStore.MAX_FILE_BYTES));
            }
        }

        private static ItemStack icon() {
            ItemStack stack = new ItemStack(Items.dye, 1, 4);
            NBTTagCompound tag = new NBTTagCompound(); tag.setString("name", "original"); stack.setTagCompound(tag);
            return stack;
        }

        private static NBTTagCompound label(LabelTestSupport.TestWorld world, int x, int y, int z) {
            HologramLabel label = new HologramLabel(world, x, y, z); label.setLabel("label"); return label.toNBT();
        }

        private static NBTTagCompound root(NBTTagCompound... labels) {
            NBTTagCompound root = new NBTTagCompound(); NBTTagList list = new NBTTagList();
            for (NBTTagCompound label : labels) list.appendTag(label);
            root.setTag("Labels", list); return root;
        }

        private static NBTTagCompound manyLabels(LabelTestSupport.TestWorld world, int count, boolean oneChunk) {
            NBTTagCompound root = root(); NBTTagList labels = root.getTagList("Labels", 10);
            for (int i = 0; i < count; i++) labels.appendTag(oneChunk
                ? label(world, -16 + (i & 15), 64 + i / 256, -16 + ((i >> 4) & 15))
                : label(world, i * 16, 64, 0));
            return root;
        }

        private static void write(Path file, NBTTagCompound root) throws IOException { Files.write(file, LabelNbt.encode(root, LocalLabelStore.MAX_FILE_BYTES)); }
        private static File backup(File file) { return new File(file.getPath() + ".bak"); }

        private static void corrupt(Path path, LabelTestSupport.TestWorld world, byte[] bytes) throws Exception {
            Files.write(path, bytes);
            IOException failure = assertThrows(IOException.class, () -> new LocalLabelStore(path.toFile(), world));
            assertTrue(failure.getMessage().contains(path.toString()));
            assertArrayEquals(bytes, Files.readAllBytes(path));
        }

        private static final class Directory implements AutoCloseable {
            final Path path = Files.createTempDirectory("hologram-local-store-");
            Directory() throws IOException {}
            @Override public void close() throws IOException {
                Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                    @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                        Files.delete(file); return FileVisitResult.CONTINUE;
                    }
                    @Override public FileVisitResult postVisitDirectory(Path directory, IOException failure) throws IOException {
                        if (failure != null) throw failure;
                        Files.delete(directory); return FileVisitResult.CONTINUE;
                    }
                });
            }
        }
    }
}
