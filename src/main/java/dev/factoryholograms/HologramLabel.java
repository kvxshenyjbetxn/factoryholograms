package dev.factoryholograms;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;

/** Local annotation/draft data; never attached to a real inventory, container or tile entity. */
public class HologramLabel implements IInventory {
    public final int x, y, z;
    private World world;
    private final ItemStack[] icons = new ItemStack[LabelRules.ICON_SLOTS];
    private String title = "";
    private float offsetX, offsetY, offsetZ;
    private String blockName = "";
    private int blockMeta;


    public HologramLabel(World world, int x, int y, int z) {
        this(world, x, y, z, true);
    }

    private HologramLabel(World world, int x, int y, int z, boolean captureBlock) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        if (captureBlock && world != null && world.blockExists(x, y, z)) {
            Object name = Block.blockRegistry.getNameForObject(world.getBlock(x, y, z));
            blockName = name == null || name.toString().length() > 256 ? "" : name.toString();
            blockMeta = world.getBlockMetadata(x, y, z);
        }
    }

    public World getWorld() { return world; }
    public String getTitle() { return title; }
    public float getOffsetX() { return offsetX; }
    public float getOffsetY() { return offsetY; }
    public float getOffsetZ() { return offsetZ; }
    public void setLabel(String text) { setLabel(text, offsetX, offsetY, offsetZ); }

    public void setLabel(String text, float offsetX, float offsetY, float offsetZ) {
        title = LabelRules.title(text);
        this.offsetX = LabelRules.offset(offsetX);
        this.offsetY = LabelRules.offset(offsetY);
        this.offsetZ = LabelRules.offset(offsetZ);
        markDirty();
    }

    public boolean matchesBlock() {
        // Metadata can change when a machine rotates or changes state; do not discard its label.
        return world != null && world.blockExists(x, y, z) && !world.isAirBlock(x, y, z)
            && blockName.equals(Block.blockRegistry.getNameForObject(world.getBlock(x, y, z)));
    }

    public boolean isEmpty() {
        if (!title.isEmpty()) return false;
        for (ItemStack icon : icons) if (icon != null) return false;
        return true;
    }

    @Override public boolean isUseableByPlayer(EntityPlayer player) {
        return player != null && player.worldObj == world && !player.isDead && matchesBlock()
            && player.getDistanceSq(x + 0.5, y + 0.5, z + 0.5) <= 36.0;
    }

    /** Refreshes the cached client object without breaking an open GUI's inventory reference. */
    public void copyFrom(HologramLabel other) {
        if (x != other.x || y != other.y || z != other.z) throw new IllegalArgumentException("Different label position");
        title = other.title;
        offsetX = other.offsetX;
        offsetY = other.offsetY;
        offsetZ = other.offsetZ;
        blockName = other.blockName;
        blockMeta = other.blockMeta;
        for (int i = 0; i < icons.length; i++) icons[i] = other.icons[i] == null ? null : other.icons[i].copy();
    }

    public NBTTagCompound toNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("x", x);
        tag.setInteger("y", y);
        tag.setInteger("z", z);
        tag.setString("Block", blockName);
        tag.setInteger("Meta", blockMeta);
        tag.setString("Title", title);
        tag.setInteger("LabelFormat", 2);
        tag.setFloat("OffsetX", offsetX);
        tag.setFloat("OffsetY", offsetY);
        tag.setFloat("OffsetZ", offsetZ);
        NBTTagList list = new NBTTagList();
        for (int i = 0; i < icons.length; i++) {
            if (icons[i] == null) continue;
            list.appendTag(iconTag(icons[i], i));
        }
        tag.setTag("Icons", list);
        return tag;
    }

    public static HologramLabel fromNBT(World world, NBTTagCompound tag) {
        HologramLabel label = new HologramLabel(world, tag.getInteger("x"), tag.getInteger("y"), tag.getInteger("z"), false);
        label.title = LabelRules.title(tag.getString("Title"));
        label.offsetX = LabelRules.offset(tag.getFloat("OffsetX"));
        label.offsetY = LabelRules.offset(tag.getFloat("OffsetY"));
        boolean legacy = tag.getInteger("LabelFormat") < 2;
        label.offsetZ = LabelRules.offset(tag.getFloat("OffsetZ") - (legacy ? LabelRules.BASE_Z : 0));
        label.blockName = tag.getString("Block");
        if (label.blockName.length() > 256) label.blockName = "";
        label.blockMeta = tag.getInteger("Meta");
        NBTTagList list = tag.getTagList("Icons", 10);
        int iconBytes = 0;
        for (int i = 0; i < Math.min(LabelRules.ICON_SLOTS, list.tagCount()); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            int slot = entry.getByte("Slot") & 255;
            if (legacy) {
                if (slot >= 6) continue;
                slot = slot < 3 ? slot : LabelRules.ICONS_PER_SIDE + slot - 3;
            }
            if (slot >= label.icons.length || !LabelNbt.fits(entry, LabelRules.MAX_ICON_BYTES)) continue;
            int bytes = LabelNbt.encode(entry, LabelRules.MAX_ICON_BYTES).length;
            if (iconBytes + bytes > LabelRules.MAX_LABEL_ICON_BYTES) continue;
            ItemStack stack;
            if (entry.hasKey("RegistryName", 8)) {
                Item item = (Item) Item.itemRegistry.getObject(entry.getString("RegistryName"));
                stack = item == null ? null : new ItemStack(item, 1, entry.getShort("Damage"));
                if (stack != null && entry.hasKey("tag", 10)) stack.setTagCompound((NBTTagCompound) entry.getCompoundTag("tag").copy());
            } else {
                stack = ItemStack.loadItemStackFromNBT((NBTTagCompound) entry.copy());
            }
            if (stack != null) { stack.stackSize = 1; iconBytes += bytes; }
            label.icons[slot] = stack;
        }
        return label;
    }

    public int[] occupiedSlots(boolean output) {
        int start = output ? LabelRules.ICONS_PER_SIDE : 0;
        int count = 0;
        for (int i = start; i < start + LabelRules.ICONS_PER_SIDE; i++) if (icons[i] != null) count++;
        int[] slots = new int[count];
        int index = 0;
        for (int i = start; i < start + LabelRules.ICONS_PER_SIDE; i++) if (icons[i] != null) slots[index++] = i;
        return slots;
    }

    public boolean isSlotVisible(int slot) {
        if (slot < 0 || slot >= icons.length) return false;
        if (icons[slot] != null) return true;
        int start = slot < LabelRules.ICONS_PER_SIDE ? 0 : LabelRules.ICONS_PER_SIDE;
        for (int i = start; i < slot; i++) if (icons[i] == null) return false;
        return true;
    }

    @Override public int getSizeInventory() { return icons.length; }
    @Override public ItemStack getStackInSlot(int slot) { return slot >= 0 && slot < icons.length ? icons[slot] : null; }
    @Override public ItemStack decrStackSize(int slot, int count) { return null; }
    @Override public ItemStack getStackInSlotOnClosing(int slot) { return null; }
    @Override public String getInventoryName() { return "factoryholograms.label"; }
    @Override public boolean hasCustomInventoryName() { return false; }
    @Override public int getInventoryStackLimit() { return 1; }
    @Override public boolean isItemValidForSlot(int slot, ItemStack stack) { return false; }
    @Override public void openInventory() {}
    @Override public void closeInventory() {}
    @Override public void markDirty() {}

    private static NBTTagCompound iconTag(ItemStack stack, int slot) {
        NBTTagCompound entry = new NBTTagCompound();
        stack.writeToNBT(entry);
        Object name = Item.itemRegistry.getNameForObject(stack.getItem());
        if (name != null) entry.setString("RegistryName", name.toString());
        entry.setByte("Slot", (byte) slot);
        return entry;
    }

    @Override public void setInventorySlotContents(int slot, ItemStack stack) {
        if (slot < 0 || slot >= icons.length) return;
        ItemStack copy = stack == null ? null : stack.copy();
        if (copy != null) {
            copy.stackSize = 1;
            NBTTagCompound entry = iconTag(copy, slot);
            if (!LabelNbt.fits(entry, LabelRules.MAX_ICON_BYTES)) return;
            int bytes = LabelNbt.encode(entry, LabelRules.MAX_ICON_BYTES).length;
            for (int i = 0; i < icons.length; i++) {
                if (i == slot || icons[i] == null) continue;
                NBTTagCompound existing = iconTag(icons[i], i);
                bytes += LabelNbt.encode(existing, LabelRules.MAX_ICON_BYTES).length;
            }
            if (bytes > LabelRules.MAX_LABEL_ICON_BYTES) return;
        }
        icons[slot] = copy;
        markDirty();
    }
}
