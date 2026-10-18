package dev.factoryholograms;

import net.minecraft.item.ItemStack;

/** A private cursor copy, never the player's real carried stack or an NEI-owned stack. */
public final class GhostDrag {
    private ItemStack stack;
    private int pressX, pressY;
    private boolean moved;

    public void begin(ItemStack source, int x, int y) {
        clear();
        if (source == null || source.getItem() == null) return;
        stack = source.copy();
        stack.stackSize = 1;
        pressX = x;
        pressY = y;
    }

    public boolean active() { return stack != null; }
    public ItemStack stack() { return stack == null ? null : stack.copy(); }

    public boolean movedTo(int x, int y) {
        if (!active()) return false;
        long dx = (long) x - pressX;
        long dy = (long) y - pressY;
        // Small pointer jitter still counts as a click, including when releasing over the source.
        moved |= Math.abs(dx) > 3 || Math.abs(dy) > 3 || dx * dx + dy * dy > 9;
        return moved;
    }

    public void clear() {
        stack = null;
        moved = false;
    }
}
