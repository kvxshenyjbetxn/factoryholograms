package dev.factoryholograms.client;

import net.minecraft.item.ItemStack;

/** Optional item-browser boundary; the editor and inventory-only mode never link NEI classes. */
public interface ItemBrowser {
    void beforeDraw(HologramGui gui);
    void draw(HologramGui gui, int mouseX, int mouseY);
    void drawTooltips(HologramGui gui, int mouseX, int mouseY);
    ItemStack stackAt(HologramGui gui, int x, int y);
    boolean mouseDown(HologramGui gui, int x, int y, int button);
    boolean mouseWheel(HologramGui gui, int x, int y, int delta);
    boolean keyTyped(HologramGui gui, char character, int key);
    void close(HologramGui gui);

    ItemBrowser NONE = new ItemBrowser() {
        @Override public void beforeDraw(HologramGui gui) {}
        @Override public void draw(HologramGui gui, int mouseX, int mouseY) {}
        @Override public void drawTooltips(HologramGui gui, int mouseX, int mouseY) {}
        @Override public ItemStack stackAt(HologramGui gui, int x, int y) { return null; }
        @Override public boolean mouseDown(HologramGui gui, int x, int y, int button) { return false; }
        @Override public boolean mouseWheel(HologramGui gui, int x, int y, int delta) { return false; }
        @Override public boolean keyTyped(HologramGui gui, char character, int key) { return false; }
        @Override public void close(HologramGui gui) {}
    };
}
