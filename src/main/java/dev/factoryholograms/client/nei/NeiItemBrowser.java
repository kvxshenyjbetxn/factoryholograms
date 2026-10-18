package dev.factoryholograms.client.nei;

import java.awt.Point;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import codechicken.lib.gui.GuiDraw;
import codechicken.nei.ItemPanels;
import codechicken.nei.KeyManager;
import codechicken.nei.LayoutManager;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.PanelWidget;
import codechicken.nei.SearchField;
import codechicken.nei.VisiblityData;
import codechicken.nei.Widget;
import codechicken.nei.api.API;
import codechicken.nei.api.INEIGuiAdapter;
import codechicken.nei.guihook.GuiContainerManager;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import dev.factoryholograms.client.HologramGui;
import dev.factoryholograms.client.ItemBrowser;

/** NEI supplies presentation and search; the editor owns all item drag gestures. */
@SideOnly(Side.CLIENT)
public final class NeiItemBrowser implements ItemBrowser {

    private static boolean handlerRegistered;

    private static GuiContainerManager manager(HologramGui gui) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null || mc.currentScreen != gui
            || !NEIClientConfig.isLoaded() || LayoutManager.instance() == null) return null;

        // The transformer creates this only after initGui returns. Never create/load a second manager.
        GuiContainerManager manager = GuiContainerManager.getManager(gui);
        if (manager != null && !handlerRegistered) {
            API.registerNEIGuiHandler(new GuiHandler());
            handlerRegistered = true;
            LayoutManager.layout(gui);
        }
        return manager;
    }

    @Override
    public void beforeDraw(HologramGui gui) {
        GuiContainerManager manager = manager(gui);
        if (manager == null) return;
        if (gui.isTitleFocused() || !NEIClientConfig.isEnabled() || NEIClientConfig.isHidden()) {
            LayoutManager.searchField.setFocus(false);
        }
        manager.preDraw();
    }

    @Override
    public void draw(HologramGui gui, int mouseX, int mouseY) {
        GuiContainerManager manager = manager(gui);
        if (manager == null) return;
        GL11.glPushMatrix();
        try {
            // renderObjects cancels the container-local translation used by vanilla's renderer.
            GL11.glTranslatef(gui.getPanelLeft(), gui.getPanelTop(), 0);
            manager.renderObjects(mouseX, mouseY);
        } finally {
            GL11.glPopMatrix();
        }
    }

    @Override
    public void drawTooltips(HologramGui gui, int mouseX, int mouseY) {
        Widget widget = widgetAt(gui, mouseX, mouseY);
        if (widget == null || (pagePanel(widget) == null && widget != LayoutManager.searchField)) return;
        GuiContainerManager manager = manager(gui);
        if (manager != null) manager.renderToolTips(mouseX, mouseY);
    }

    @Override
    public ItemStack stackAt(HologramGui gui, int x, int y) {
        Widget widget = widgetAt(gui, x, y);
        if (widget == null || (widget != ItemPanels.itemPanel && widget != ItemPanels.bookmarkPanel)) return null;
        return iconCopy(widget.getStackMouseOver(x, y));
    }

    @Override
    public boolean mouseDown(HologramGui gui, int x, int y, int button) {
        if (manager(gui) == null) return false;
        Widget widget = widgetAt(gui, x, y);
        SearchField search = LayoutManager.searchField;
        if (widget != search) search.setFocus(false);

        if (widget != null && widget == search && search.isVisible()) {
            if (button == 0 || button == 1) search.handleClick(x, y, button);
            return true;
        }
        PanelWidget<?> panel = pagePanel(widget);
        if (panel != null && widget != panel) {
            if (button == 0 || button == 1) widget.handleClick(x, y, button);
            return true;
        }
        // In particular, never call a panel's handleClick/handleClickExt, even with an empty cursor.
        return false;
    }

    @Override
    public boolean mouseWheel(HologramGui gui, int x, int y, int delta) {
        PanelWidget<?> panel = pagePanel(widgetAt(gui, x, y));
        if (panel == null || delta == 0) return false;
        // BookmarkPanel.onMouseWheel also edits quantities/namespaces; only paging is allowed here.
        panel.getGrid().shiftPage(delta > 0 ? -1 : 1);
        return true;
    }

    @Override
    public boolean keyTyped(HologramGui gui, char character, int key) {
        if (manager(gui) == null) return false;
        SearchField search = LayoutManager.searchField;
        if (gui.isTitleFocused()) {
            search.setFocus(false);
            return false;
        }
        if (!NEIClientConfig.isEnabled() || NEIClientConfig.isHidden()) search.setFocus(false);
        if (!NEIClientConfig.isEnabled()) return false;

        if (!NEIClientConfig.isHidden() && search.isVisible() && search.focused()) {
            if (matchesKey("gui.getprevioussearch", key) || matchesKey("gui.getnextsearch", key)) {
                search.lastKeyTyped(key, character);
            } else {
                search.handleKeyPress(key, character);
            }
            return true;
        }
        if (matchesKey("gui.hide", key)) {
            if (!Keyboard.isRepeatEvent()) {
                NEIClientConfig.toggleBooleanSetting("inventory.hidden");
                search.setFocus(false);
                LayoutManager.layout(gui);
            }
            return true;
        }
        if (NEIClientConfig.isHidden()) return false;
        if (search.isVisible() && matchesKey("gui.search", key)) {
            search.setFocus(true);
            return true;
        }

        int direction = matchesKey("gui.next", key) ? 1 : matchesKey("gui.prev", key) ? -1 : 0;
        if (direction == 0) return false;
        Point mouse = GuiDraw.getMousePosition();
        PanelWidget<?> panel = pagePanel(widgetAt(gui, mouse.x, mouse.y));
        if (panel == null) return false;
        panel.getGrid().shiftPage(direction);
        return true;
    }

    @Override
    public void close(HologramGui gui) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld != null && mc.currentScreen == gui && NEIClientConfig.isLoaded()
            && LayoutManager.searchField != null) {
            LayoutManager.searchField.setFocus(false);
        }
    }

    private static Widget widgetAt(HologramGui gui, int x, int y) {
        if (manager(gui) == null || !NEIClientConfig.isEnabled() || NEIClientConfig.isHidden()
            || overlaps(gui, x, y, 1, 1)) return null;
        return LayoutManager.instance().getWidgetUnderMouse(x, y);
    }

    private static PanelWidget<?> pagePanel(Widget widget) {
        if (widget == null) return null;
        PanelWidget<?> items = ItemPanels.itemPanel;
        PanelWidget<?> bookmarks = ItemPanels.bookmarkPanel;
        if (items != null && (widget == items || widget == items.pagePrev || widget == items.pageNext)) return items;
        if (bookmarks != null
            && (widget == bookmarks || widget == bookmarks.pagePrev || widget == bookmarks.pageNext)) return bookmarks;
        return null;
    }

    private static boolean matchesKey(String binding, int key) {
        // Match this event, not an unrelated key that happens to be held at the same time.
        return key != Keyboard.KEY_NONE && key == KeyManager.getKeyCode(binding) && KeyManager.isHashDown(binding);
    }

    private static ItemStack iconCopy(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return null;
        ItemStack copy = stack.copy();
        copy.stackSize = 1;
        return copy;
    }

    private static boolean overlaps(HologramGui gui, int x, int y, int width, int height) {
        return x < gui.getPanelLeft() + gui.getPanelWidth() && x + width > gui.getPanelLeft()
            && y < gui.getPanelTop() + gui.getPanelHeight() && y + height > gui.getPanelTop();
    }

    @SideOnly(Side.CLIENT)
    private static final class GuiHandler extends INEIGuiAdapter {

        @Override
        public VisiblityData modifyVisiblity(GuiContainer gui, VisiblityData visibility) {
            if (gui instanceof HologramGui) {
                visibility.showUtilityButtons = false;
                visibility.enableDeleteMode = false;
            }
            return visibility;
        }

        @Override
        public boolean hideItemPanelSlot(GuiContainer gui, int x, int y, int width, int height) {
            return gui instanceof HologramGui && overlaps((HologramGui) gui, x, y, width, height);
        }

        @Override
        public boolean handleDragNDrop(GuiContainer gui, int x, int y, ItemStack stack, int button) {
            if (!(gui instanceof HologramGui)) return false;
            HologramGui editor = (HologramGui) gui;
            Minecraft mc = Minecraft.getMinecraft();
            if (button == 0 && mc.theWorld != null && mc.thePlayer != null && mc.currentScreen == gui
                && NEIClientConfig.isLoaded() && NEIClientConfig.isEnabled() && !NEIClientConfig.isHidden()) {
                ItemStack copy = iconCopy(stack);
                if (copy != null) editor.acceptBrowserDrop(x, y, copy);
            }
            // Rejected editor drops must not fall through to inventory/cheat handlers. Never consume the source stack.
            return true;
        }
    }
}
