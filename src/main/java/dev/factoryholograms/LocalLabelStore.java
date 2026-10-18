package dev.factoryholograms;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;

/**
 * Local-only label snapshots, independent of Minecraft's client, saved-data and networking APIs.
 * To import old labels, close the client and manually COPY factoryholograms_labels.dat to the
 * appropriate LocalWorldKey.dataFile(...). Never move/edit the server's original file. Direct
 * legacy GZIP roots and vanilla's {data: {Labels: ...}} wrapper are accepted; only an intentional
 * put/remove rewrites the target as raw NBT. The previous target is retained as target.dat.bak.
 * Block matching/replacement belongs to the caller, not this store.
 */
public final class LocalLabelStore {
    public static final int MAX_LABELS = 4096;
    public static final int MAX_FILE_BYTES = 16 * 1024 * 1024;
    private static final int FORMAT = 1;
    private static final int WORLD_BOUND = 30000000;

    private final File file;
    private final World world;
    private Map<String, HologramLabel> saved;

    public LocalLabelStore(File file, World world) throws IOException {
        if (file == null || world == null) throw new IllegalArgumentException("File and world are required");
        this.file = file.getAbsoluteFile();
        this.world = world;
        saved = Files.notExists(this.file.toPath()) ? new LinkedHashMap<String, HologramLabel>() : read(this.file.toPath());
    }

    public File getFile() { return file; }

    /** An unmodifiable, deep snapshot: inventory edits cannot mutate committed state. */
    public synchronized Collection<HologramLabel> labels() {
        Collection<HologramLabel> result = new ArrayList<HologramLabel>(saved.size());
        for (HologramLabel label : saved.values()) result.add(copy(label));
        return Collections.unmodifiableCollection(result);
    }

    /** No persistence, block validation or chunk loading; an existing anchor is copied unchanged. */
    public synchronized HologramLabel draft(int x, int y, int z) {
        checkPosition(x, y, z);
        HologramLabel label = saved.get(key(x, y, z));
        return label == null ? new HologramLabel(world, x, y, z) : copy(label);
    }

    /** Only a new/replaced anchor receives automatic placement; existing saved positions stay intact. */
    public synchronized HologramLabel draftForEditing(int x, int y, int z, int frontFace) {
        checkPosition(x, y, z);
        HologramLabel existing = saved.get(key(x, y, z));
        if (existing != null && (!world.blockExists(x, y, z) || existing.matchesBlock())) return copy(existing);
        HologramLabel draft = new HologramLabel(world, x, y, z);
        float[] offsets = HologramPlacement.frontOffsets(frontFace);
        draft.setLabel("", offsets[0], offsets[1], offsets[2]);
        return draft;
    }

    /** Returns whether a snapshot was committed. Blank drafts remove an existing label. */
    public synchronized boolean put(HologramLabel draft) throws IOException {
        if (draft == null || draft.getWorld() != world) throw new IllegalArgumentException("Label belongs to a different world");
        checkPosition(draft.x, draft.y, draft.z);
        String key = key(draft.x, draft.y, draft.z);
        if (draft.isEmpty()) {
            if (!saved.containsKey(key)) return false;
            remove(draft.x, draft.y, draft.z);
            return true;
        }
        NBTTagCompound tag = draft.toNBT();
        validateLabel(tag);
        HologramLabel previous = saved.get(key);
        if (previous != null && previous.toNBT().equals(tag)) return false;
        Map<String, HologramLabel> next = new LinkedHashMap<String, HologramLabel>(saved);
        next.put(key, HologramLabel.fromNBT(world, tag));
        validateCounts(next);
        commit(next);
        return true;
    }

    public synchronized void remove(int x, int y, int z) throws IOException {
        checkPosition(x, y, z);
        String key = key(x, y, z);
        if (!saved.containsKey(key)) return;
        Map<String, HologramLabel> next = new LinkedHashMap<String, HologramLabel>(saved);
        next.remove(key);
        commit(next);
    }

    private HologramLabel copy(HologramLabel label) { return HologramLabel.fromNBT(world, label.toNBT()); }

    private void commit(Map<String, HologramLabel> next) throws IOException {
        NBTTagCompound root = new NBTTagCompound();
        root.setInteger("Format", FORMAT);
        NBTTagList labels = new NBTTagList();
        for (HologramLabel label : next.values()) labels.appendTag(label.toNBT());
        root.setTag("Labels", labels);
        final byte[] bytes;
        try {
            bytes = LabelNbt.encode(root, MAX_FILE_BYTES);
            // The decoder's allocation tracker can be stricter than the on-disk byte limit.
            LabelNbt.decode(bytes);
        } catch (RuntimeException invalid) {
            throw new IOException("Local labels exceed the bounded NBT budget for " + file, invalid);
        }
        write(bytes);
        saved = next;
    }

    private Map<String, HologramLabel> read(Path path) throws IOException {
        try {
            if (Files.size(path) > MAX_FILE_BYTES) throw new IOException("File exceeds " + MAX_FILE_BYTES + " bytes");
            byte[] bytes;
            try (InputStream input = Files.newInputStream(path)) { bytes = boundedBytes(input); }
            if (bytes.length >= 2 && (bytes[0] & 255) == 0x1f && (bytes[1] & 255) == 0x8b) {
                try (InputStream gzip = new GZIPInputStream(new ByteArrayInputStream(bytes))) { bytes = boundedBytes(gzip); }
            }
            NBTTagCompound root = LabelNbt.decode(bytes);
            validateFormat(root);
            if (!root.hasKey("Labels") && root.hasKey("data", 10)) root = root.getCompoundTag("data");
            validateFormat(root);
            if (!root.hasKey("Labels", 9)) throw new IOException("Missing or malformed Labels list");
            NBTTagList labels = (NBTTagList) root.getTag("Labels");
            if (labels.func_150303_d() != 0 && labels.func_150303_d() != 10) throw new IOException("Labels must contain compounds");
            if (labels.tagCount() > MAX_LABELS) throw new IOException("More than " + MAX_LABELS + " labels");
            Map<String, HologramLabel> result = new LinkedHashMap<String, HologramLabel>();
            for (int i = 0; i < labels.tagCount(); i++) {
                NBTTagCompound tag = labels.getCompoundTagAt(i);
                validateLabel(tag);
                HologramLabel label = HologramLabel.fromNBT(world, tag);
                String key = key(label.x, label.y, label.z);
                if (result.put(key, label) != null) throw new IOException("Duplicate label at " + key);
            }
            validateCounts(result);
            // Count/validate all entries before discarding legacy blank snapshots.
            java.util.Iterator<HologramLabel> iterator = result.values().iterator();
            while (iterator.hasNext()) if (iterator.next().isEmpty()) iterator.remove();
            return result;
        } catch (IOException | RuntimeException invalid) {
            throw new IOException("Cannot read local labels from " + path + ": " + invalid.getMessage()
                + ". File was not modified; restore a known-good .bak or choose another target.", invalid);
        }
    }

    private static void validateFormat(NBTTagCompound root) throws IOException {
        if (root.hasKey("Format") && (!root.hasKey("Format", 3) || root.getInteger("Format") != FORMAT)) {
            throw new IOException("Unsupported local label Format");
        }
    }

    private static void validateLabel(NBTTagCompound tag) throws IOException {
        if (!tag.hasKey("x", 3) || !tag.hasKey("y", 3) || !tag.hasKey("z", 3)) throw new IOException("Missing integer label coordinates");
        try { checkPosition(tag.getInteger("x"), tag.getInteger("y"), tag.getInteger("z")); }
        catch (IllegalArgumentException invalid) { throw new IOException(invalid.getMessage(), invalid); }
        if (tag.hasKey("LabelFormat") && (!tag.hasKey("LabelFormat", 3)
            || tag.getInteger("LabelFormat") < 0 || tag.getInteger("LabelFormat") > 2)) {
            throw new IOException("Unsupported LabelFormat");
        }
        if (!tag.hasKey("Icons")) return;
        if (!tag.hasKey("Icons", 9)) throw new IOException("Malformed Icons list");
        NBTTagList icons = (NBTTagList) tag.getTag("Icons");
        if (icons.func_150303_d() != 0 && icons.func_150303_d() != 10) throw new IOException("Icons must contain compounds");
        if (icons.tagCount() > LabelRules.ICON_SLOTS) throw new IOException("Too many label icons");
        int total = 0;
        try {
            for (int i = 0; i < icons.tagCount(); i++) total += LabelNbt.encode(icons.getCompoundTagAt(i), LabelRules.MAX_ICON_BYTES).length;
        } catch (IllegalArgumentException invalid) { throw new IOException("Label icon exceeds its NBT budget", invalid); }
        if (total > LabelRules.MAX_LABEL_ICON_BYTES) throw new IOException("Label icons exceed their total NBT budget");
    }

    private static void validateCounts(Map<String, HologramLabel> labels) throws IOException {
        if (labels.size() > MAX_LABELS) throw new IOException("More than " + MAX_LABELS + " local labels");
        Map<String, Integer> chunks = new HashMap<String, Integer>();
        for (HologramLabel label : labels.values()) {
            String chunk = (label.x >> 4) + ":" + (label.z >> 4);
            Integer count = chunks.get(chunk);
            int next = count == null ? 1 : count + 1;
            if (next > LabelRules.MAX_LABELS_PER_CHUNK) throw new IOException("More than " + LabelRules.MAX_LABELS_PER_CHUNK + " labels in chunk " + chunk);
            chunks.put(chunk, next);
        }
    }

    private static byte[] boundedBytes(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (count > MAX_FILE_BYTES - output.size()) throw new IOException("NBT exceeds " + MAX_FILE_BYTES + " bytes");
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private void write(byte[] bytes) throws IOException {
        Path target = file.toPath();
        Path backup = target.resolveSibling(file.getName() + ".bak");
        Path temporary = null;
        Path backupTemporary = null;
        try {
            Files.createDirectories(target.getParent());
            temporary = Files.createTempFile(target.getParent(), file.getName() + ".", ".tmp");
            Files.write(temporary, bytes);
            if (!Files.notExists(target)) {
                // Revalidate even after construction: never overwrite a file that became corrupt.
                read(target);
                backupTemporary = Files.createTempFile(target.getParent(), file.getName() + ".", ".bak.tmp");
                Files.copy(target, backupTemporary, StandardCopyOption.REPLACE_EXISTING);
                replace(backupTemporary, backup);
            }
            replace(temporary, target);
        } catch (IOException | RuntimeException failure) {
            throw new IOException("Could not save local labels to " + file + ": " + failure.getMessage()
                + ". Memory was not committed. Previous data may be recovered from " + backup + ".", failure);
        } finally {
            cleanup(temporary);
            cleanup(backupTemporary);
        }
    }

    private static void replace(Path source, Path target) throws IOException {
        try { Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException unsupported) { Files.move(source, target, StandardCopyOption.REPLACE_EXISTING); }
    }

    private static void cleanup(Path temporary) {
        if (temporary == null) return;
        try { Files.deleteIfExists(temporary); }
        catch (IOException | SecurityException ignored) {
            // A cleanup failure must not report a successful disk replacement as a failed commit.
            try { temporary.toFile().deleteOnExit(); }
            catch (SecurityException alsoIgnored) { /* Keep the committed disk/memory state consistent. */ }
        }
    }

    private static void checkPosition(int x, int y, int z) {
        if (y < 0 || y > 255 || x < -WORLD_BOUND || x > WORLD_BOUND || z < -WORLD_BOUND || z > WORLD_BOUND) {
            throw new IllegalArgumentException("Label position outside world bounds: " + key(x, y, z));
        }
    }

    private static String key(int x, int y, int z) { return x + ":" + y + ":" + z; }
}
