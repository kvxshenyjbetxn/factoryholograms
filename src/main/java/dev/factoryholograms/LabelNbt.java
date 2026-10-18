package dev.factoryholograms;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTSizeTracker;

/** Raw, bounded NBT avoids the signed-short compressed NBT limit in Forge's ByteBufUtils. */
final class LabelNbt {
    private LabelNbt() {}

    static byte[] encode(NBTTagCompound tag, final int limit) {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            CompressedStreamTools.write(tag, new DataOutputStream(new OutputStream() {
                @Override public void write(int value) throws IOException {
                    if (bytes.size() >= limit) throw new IOException("Hologram NBT too large");
                    bytes.write(value);
                }
                @Override public void write(byte[] data, int offset, int length) throws IOException {
                    if (length > limit - bytes.size()) throw new IOException("Hologram NBT too large");
                    bytes.write(data, offset, length);
                }
            }));
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalArgumentException("Hologram NBT too large or invalid", e);
        }
    }

    static boolean fits(NBTTagCompound tag, int limit) {
        try { encode(tag, limit); return true; }
        catch (IllegalArgumentException e) { return false; }
    }

    static NBTTagCompound decode(byte[] bytes) {
        try {
            DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes));
            NBTTagCompound tag = CompressedStreamTools.func_152456_a(input, new NBTSizeTracker(16 * 1024 * 1024));
            if (input.available() != 0) throw new IllegalArgumentException("Trailing hologram NBT");
            return tag;
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid hologram NBT", e);
        }
    }
}
