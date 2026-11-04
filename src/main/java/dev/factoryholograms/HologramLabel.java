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
    private float yaw, pitch;
    private int placementFace = 3;
    private String blockName = "";
    private int blockMeta;
    private int recipeCount = 1;
    private final String[] recipeNames = new String[LabelRules.MAX_RECIPES];

    {
        java.util.Arrays.fill(recipeNames, "");
    }

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
    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }
    public void setRotation(float yaw, float pitch) {
        this.yaw = LabelRules.angle(yaw, 180.0F);
        this.pitch = LabelRules.angle(pitch, 90.0F);
        markDirty();
    }
    public int getPlacementFace() { return placementFace; }
    public void setPlacementFace(int face) { placementFace = face >= 2 && face <= 5 ? face : 3; }

    /** User adjustments relative to the automatic front placement, not the world anchor. */
    public float[] getPositionOffsets() {
        float[] base = HologramPlacement.frontOffsets(placementFace);
        return new float[] {offsetX - base[0], offsetY - base[1], offsetZ - base[2]};
    }
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
        placementFace = other.placementFace;
        yaw = other.yaw;
        pitch = other.pitch;
        blockName = other.blockName;
        blockMeta = other.blockMeta;
        recipeCount = other.recipeCount;
        System.arraycopy(other.recipeNames, 0, recipeNames, 0, recipeNames.length);
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
        tag.setInteger("LabelFormat", 3);
        tag.setInteger("Recipes", recipeCount);
        tag.setInteger("PlacementFace", placementFace);
        tag.setFloat("OffsetX", offsetX);
        tag.setFloat("OffsetY", offsetY);
        tag.setFloat("OffsetZ", offsetZ);
        tag.setFloat("Yaw", yaw);
        tag.setFloat("Pitch", pitch);
        NBTTagList list = new NBTTagList();
        for (int i = 0; i < icons.length; i++) {
            if (icons[i] == null) continue;
            list.appendTag(iconTag(icons[i], i));
        }
        tag.setTag("Icons", list);
        boolean named = false;
        NBTTagList names = new NBTTagList();
        for (int r = 0; r < recipeCount; r++) {
            names.appendTag(new net.minecraft.nbt.NBTTagString(recipeNames[r]));
            named |= !recipeNames[r].isEmpty();
        }
        if (named) tag.setTag("RecipeNames", names);
        return tag;
    }

    public static HologramLabel fromNBT(World world, NBTTagCompound tag) {
        HologramLabel label = new HologramLabel(world, tag.getInteger("x"), tag.getInteger("y"), tag.getInteger("z"), false);
        label.title = LabelRules.title(tag.getString("Title"));
        label.setRotation(tag.getFloat("Yaw"), tag.getFloat("Pitch"));
        label.offsetX = LabelRules.offset(tag.getFloat("OffsetX"));
        label.offsetY = LabelRules.offset(tag.getFloat("OffsetY"));
        boolean legacy = tag.getInteger("LabelFormat") < 2;
        label.offsetZ = LabelRules.offset(tag.getFloat("OffsetZ") - (legacy ? LabelRules.BASE_Z : 0));
        if (tag.hasKey("PlacementFace", 3)) {
            label.setPlacementFace(tag.getInteger("PlacementFace"));
        } else if (!legacy) {
            // Recover untouched old front presets (saved with Y 0) without moving any existing label.
            for (int face = 2; face <= 5; face++) {
                float[] base = HologramPlacement.frontOffsets(face);
                if (label.offsetX == base[0] && label.offsetY == 0 && label.offsetZ == base[2]) {
                    label.setPlacementFace(face);
                    break;
                }
            }
        }
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
        int highestNamed = -1;
        if (tag.hasKey("RecipeNames", 9)) {
            NBTTagList names = tag.getTagList("RecipeNames", 8);
            for (int r = 0; r < Math.min(LabelRules.MAX_RECIPES, names.tagCount()); r++) {
                label.recipeNames[r] = LabelRules.recipeName(names.getStringTagAt(r));
                if (!label.recipeNames[r].isEmpty()) highestNamed = r;
            }
        }
        int highest = -1;
        for (int i = 0; i < label.icons.length; i++) if (label.icons[i] != null) highest = i;
        int stored = tag.hasKey("Recipes", 3) ? tag.getInteger("Recipes") : 1;
        label.recipeCount = Math.max(1, Math.min(LabelRules.MAX_RECIPES,
            Math.max(Math.max(stored, highestNamed + 1), highest < 0 ? 1 : LabelRules.recipeOf(highest) + 1)));
        return label;
    }

    public int getRecipeCount() { return recipeCount; }

    /** Appends an empty recipe; returns its index, or -1 at the limit. */
    public int addRecipe() {
        if (recipeCount >= LabelRules.MAX_RECIPES) return -1;
        return recipeCount++;
    }

    /** Clears recipe r and shifts later recipes down; the last remaining recipe is only cleared. */
    public void removeRecipe(int r) {
        if (r < 0 || r >= recipeCount) return;
        int from = LabelRules.slot(r, 0);
        int span = LabelRules.SLOTS_PER_RECIPE;
        System.arraycopy(icons, from + span, icons, from, icons.length - from - span);
        for (int i = icons.length - span; i < icons.length; i++) icons[i] = null;
        System.arraycopy(recipeNames, r + 1, recipeNames, r, recipeNames.length - r - 1);
        recipeNames[recipeNames.length - 1] = "";
        if (recipeCount > 1) recipeCount--;
        markDirty();
    }

    public boolean isRecipeEmpty(int r) {
        if (r < 0 || r >= LabelRules.MAX_RECIPES) return true;
        if (!recipeNames[r].isEmpty()) return false;
        for (int i = 0; i < LabelRules.SLOTS_PER_RECIPE; i++) if (icons[LabelRules.slot(r, i)] != null) return false;
        return true;
    }

    public String getRecipeName(int r) { return r < 0 || r >= recipeNames.length ? "" : recipeNames[r]; }

    public void setRecipeName(int r, String name) {
        if (r < 0 || r >= recipeNames.length) return;
        recipeNames[r] = LabelRules.recipeName(name);
        if (!recipeNames[r].isEmpty() && r >= recipeCount) recipeCount = r + 1;
        markDirty();
    }

    /** Indices of recipes that have at least one icon, in order. */
    public int[] visibleRecipes() {
        int count = 0;
        for (int r = 0; r < recipeCount; r++) if (!isRecipeEmpty(r)) count++;
        int[] result = new int[count];
        int index = 0;
        for (int r = 0; r < recipeCount; r++) if (!isRecipeEmpty(r)) result[index++] = r;
        return result;
    }

    /** Occupied global slots of recipe 0. */
    public int[] occupiedSlots(boolean output) { return occupiedSlots(0, output); }

    /** Occupied global slots of one side of a recipe. */
    public int[] occupiedSlots(int recipe, boolean output) {
        int start = LabelRules.slot(recipe, output ? LabelRules.ICONS_PER_SIDE : 0);
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
        int start = slot - (LabelRules.isInputSlot(slot) ? LabelRules.localSlot(slot) : LabelRules.localSlot(slot) - LabelRules.ICONS_PER_SIDE);
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
        if (copy != null && LabelRules.recipeOf(slot) >= recipeCount) recipeCount = LabelRules.recipeOf(slot) + 1;
        markDirty();
    }
}
