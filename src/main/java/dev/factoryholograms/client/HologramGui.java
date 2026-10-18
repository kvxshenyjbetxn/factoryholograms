package dev.factoryholograms.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import dev.factoryholograms.FactoryHolograms;
import dev.factoryholograms.GhostDrag;
import dev.factoryholograms.HologramConfig;
import dev.factoryholograms.HologramLabel;
import dev.factoryholograms.HologramPlacement;

import dev.factoryholograms.LabelRules;

/** Edits only the defensive draft supplied by the client owner; Apply is the sole save path. */
@SideOnly(Side.CLIENT)
public final class HologramGui extends GuiContainer {

    private static final int APPLY = 0;
    private static final int DELETE = 1;
    private static final int TOGGLE = 2;
    private static final int POSITION_FIRST = 10;
    private static final int POSITION_RESET = 16;

    private static final int POSITION_MODE = 18;
    private static final int ROTATION_MODE = 19;
    private static final int ACCENT = 0x65E5ED;
    private static final int GHOST_Y = 56;
    private static final int INVENTORY_Y = 160;
    private static final int HOTBAR_Y = 218;

    private static boolean browserFailureLogged;
    private final HologramLabel draft;
    private final ClientProxy owner;

    private final float[] offsets = new float[3];
    private final int[] ghostX = new int[LabelRules.ICON_SLOTS];
    private final int[] ghostY = new int[LabelRules.ICON_SLOTS];
    private final RenderItem itemRenderer = new RenderItem();
    private final GhostDrag drag = new GhostDrag();
    private final ItemBrowser browser;
    private EntityPlayer contextPlayer;
    private boolean gestureStartedOnPress;
    private boolean gestureMoved;
    private int pressedMouseButton = -1;
    private long lastMousePress;
    private int touchPressCount;
    private GuiButton pressedButton;
    private GuiTextField titleField;
    private GuiButton displayButton;
    private boolean repeatStateSaved;
    private boolean previousRepeatState;
    private boolean placementMode;
    private boolean rotationMode;

    public HologramGui(HologramLabel draft, ClientProxy owner) {
        super(new LocalDisplayContainer());
        xSize = 304;
        ySize = 240;
        browser = createBrowser();
        this.draft = draft;
        this.owner = owner;

        offsets[0] = draft.getOffsetX();
        offsets[1] = draft.getOffsetY();
        offsets[2] = draft.getOffsetZ();
    }

    @Override
    public void initGui() {
        // GuiContainer.initGui assigns the player's openContainer; this display must never do that.
        clearLocalInput();
        if (contextPlayer == null) contextPlayer = mc.thePlayer;
        guiLeft = placementMode ? width - xSize - 8 : (width - xSize) / 2;
        guiTop = placementMode ? Math.max(0, height - 210 - 8) : (height - ySize) / 2;
        if (!repeatStateSaved) {
            previousRepeatState = Keyboard.areRepeatEventsEnabled();
            repeatStateSaved = true;
        }
        Keyboard.enableRepeatEvents(true);
        // Preserve the draft title on resize, but never carry an unfinished drag across layouts.
        String title = titleField == null ? draft.getTitle() : titleField.getText();
        boolean focused = !placementMode && (titleField == null || titleField.isFocused());
        titleField = new GuiTextField(fontRendererObj, guiLeft + 10, guiTop + 24, 156, 18);
        titleField.setMaxStringLength(LabelRules.MAX_TITLE);
        titleField.setText(title == null ? "" : title);
        titleField.setTextColor(0xE4F8FA);
        titleField.setFocused(focused);

        buttonList.clear();
        displayButton = null;
        if (placementMode) {
            buttonList.add(new GuiButton(APPLY, guiLeft + 184, guiTop + 164, 112, 18, text("apply", "Apply")));
        } else {
            displayButton = new GuiButton(TOGGLE, guiLeft + 184, guiTop + 148, 112, 18, "");
            buttonList.add(displayButton);
            buttonList.add(new GuiButton(APPLY, guiLeft + 8, guiTop + 130, 78, 18, text("apply", "Apply")));
            buttonList.add(new GuiButton(DELETE, guiLeft + 90, guiTop + 130, 78, 18, text("delete", "Delete")));
        }
        for (int axis = 0; axis < (rotationMode ? 2 : 3); axis++) {
            int y = guiTop + 32 + axis * 26;
            buttonList.add(new GuiButton(POSITION_FIRST + axis * 2, guiLeft + 198, y, 18, 18, "-"));
            buttonList.add(new GuiButton(POSITION_FIRST + axis * 2 + 1, guiLeft + 278, y, 18, 18, "+"));
        }
        buttonList.add(new GuiButton(POSITION_RESET, guiLeft + 184, guiTop + 116, 112, 18,
            rotationMode ? text("rotation.reset", "Reset angles") : text("position.reset", "Reset position")));

        buttonList.add(new GuiButton(POSITION_MODE, guiLeft + 184, guiTop + (placementMode ? 140 : 172), 112, 18,
            placementMode ? text("position.back", "Back to editor") : text("position.preview", "Position preview")));
        buttonList.add(new GuiButton(ROTATION_MODE, guiLeft + 184, guiTop + (placementMode ? 188 : 196), 112, 18,
            rotationMode ? text("rotation.position", "Position") : text("rotation.title", "Rotation / tilt")));
        updateGhostPositions();
        updatePositionButtons();
        updateDisplayButton();
    }

    @Override
    public void handleInput() {
        // Minecraft always calls this before updateScreen, even with empty input queues.
        if (!checkContext()) return;
        super.handleInput();
        checkContext();
    }

    @Override
    public void updateScreen() {
        // NEI requires this exact prefix or prepends a second, unguarded superclass tick.
        super.updateScreen();
        if (!checkContext()) return;
        titleField.updateCursorCounter();
        updateDisplayButton();
        updatePositionButtons();
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (!button.enabled || !checkContext()) return;
        if (button.id == APPLY) {
            if (owner.saveDraft(getPreviewLabel())) mc.displayGuiScreen(null);
            return;
        } else if (button.id == DELETE) {
            if (owner.deleteLabel(draft)) mc.displayGuiScreen(null);
            return;
        } else if (button.id == TOGGLE) {
            HologramConfig.setEnabled(!HologramConfig.enabled);
            updateDisplayButton();
        } else if (button.id == POSITION_MODE) {
            getPreviewLabel();
            if (!placementMode) browser.close(this);
            placementMode = !placementMode;
            // Re-enter the inherited resolution lifecycle so NEI rebuilds its manager after initGui.
            setWorldAndResolution(mc, width, height);
            return;
        } else if (button.id == ROTATION_MODE) {
            getPreviewLabel();
            rotationMode = !rotationMode;
            setWorldAndResolution(mc, width, height);
            return;
        } else if (button.id == POSITION_RESET) {
            if (rotationMode) {
                draft.setRotation(0, 0);
            } else {
                float[] base = HologramPlacement.frontOffsets(draft.getPlacementFace());
                System.arraycopy(base, 0, offsets, 0, offsets.length);
            }
        } else if (button.id >= POSITION_FIRST && button.id < POSITION_RESET) {
            int axis = (button.id - POSITION_FIRST) / 2;
            float step = rotationMode ? (isShiftKeyDown() ? 15.0F : 5.0F)
                : (isShiftKeyDown() ? 1.0F : LabelRules.OFFSET_STEP);
            float delta = (button.id - POSITION_FIRST) % 2 == 0 ? -step : step;
            if (rotationMode) {
                if (axis == 0) draft.setRotation(draft.getYaw() + delta, draft.getPitch());
                else draft.setRotation(draft.getYaw(), draft.getPitch() + delta);
            } else {
                offsets[axis] = LabelRules.offset(offsets[axis] + delta);
            }
        }
        getPreviewLabel();
        updatePositionButtons();
    }

    public HologramLabel getPreviewLabel() {
        draft.setLabel(titleField == null ? draft.getTitle() : titleField.getText(), offsets[0], offsets[1], offsets[2]);
        return draft;
    }

    public boolean isPlacementMode() {
        return placementMode;
    }

    private void updatePositionButtons() {
        for (Object value : buttonList) {
            GuiButton button = (GuiButton) value;
            int index = button.id - POSITION_FIRST;
            if (index >= 0 && index < 6) {
                float current = rotationMode ? (index / 2 == 0 ? draft.getYaw() : draft.getPitch()) : offsets[index / 2];
                float limit = rotationMode ? (index / 2 == 0 ? 180.0F : 90.0F) : LabelRules.MAX_OFFSET;
                button.enabled = (!rotationMode || !HologramConfig.autoRotate)
                    && (index % 2 == 0 ? current > -limit : current < limit);
            } else if (button.id == POSITION_RESET) {
                button.enabled = !rotationMode || !HologramConfig.autoRotate;
            }
        }
    }

    private void updateDisplayButton() {
        if (displayButton == null) return;
        displayButton.displayString = HologramConfig.enabled
            ? text("display.enabled", "Hide all") : text("display.disabled", "Show all");
    }

    @Override
    protected void keyTyped(char character, int keyCode) {
        if (!checkContext()) return;
        if (keyCode == Keyboard.KEY_ESCAPE) {
            if (drag.active()) clearDrag();
            else mc.displayGuiScreen(null);
            return;
        }
        if (placementMode) {
            if (keyCode == mc.gameSettings.keyBindInventory.getKeyCode()) mc.displayGuiScreen(null);
            return;
        }
        if (keyCode == Keyboard.KEY_TAB) {
            titleField.setFocused(!titleField.isFocused());
            return;
        }
        if (titleField.isFocused()) {
            // Inventory and hotbar keys are ordinary text here, never inventory actions.
            titleField.textboxKeyTyped(character, keyCode);
            return;
        }
        if (browser.keyTyped(this, character, keyCode)) return;
        if (keyCode == mc.gameSettings.keyBindInventory.getKeyCode()) mc.displayGuiScreen(null);
    }

    @Override
    public void handleKeyboardInput() {
        if (!checkContext()) return;
        if (Keyboard.getEventKeyState()) keyTyped(Keyboard.getEventCharacter(), Keyboard.getEventKey());
    }

    @Override
    public void handleMouseInput() {
        if (!checkContext()) return;
        int x = Mouse.getEventX() * width / mc.displayWidth;
        int y = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        int button = Mouse.getEventButton();
        if (Mouse.getEventButtonState()) {
            if (mc.gameSettings.touchscreen && touchPressCount++ > 0) return;
            pressedMouseButton = button;
            lastMousePress = Minecraft.getSystemTime();
            mouseClicked(x, y, button);
        } else if (button != -1) {
            if (mc.gameSettings.touchscreen) {
                // Rebuilding the layout during mouse-down already clears its press state.
                touchPressCount = Math.max(0, touchPressCount - 1);
                if (touchPressCount > 0) return;
            }
            if (pressedMouseButton == button) pressedMouseButton = -1;
            mouseMovedOrUp(x, y, button);
        } else if (pressedMouseButton != -1) {
            mouseClickMove(x, y, pressedMouseButton, Minecraft.getSystemTime() - lastMousePress);
        }
        if (!placementMode && mc.currentScreen == this && Mouse.getEventDWheel() != 0) {
            browser.mouseWheel(this, x, y, Mouse.getEventDWheel());
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (!checkContext()) return;
        if (mouseButton == 0) {
            gestureStartedOnPress = false;
            gestureMoved = false;
        }
        if (placementMode) {
            if (mouseButton == 0) pressButton(mouseX, mouseY);
            return;
        }
        // Even a panel click must reach the bridge so it can relinquish search focus.
        boolean consumed = browser.mouseDown(this, mouseX, mouseY, mouseButton);
        titleField.mouseClicked(mouseX, mouseY, mouseButton);
        if (consumed) {
            titleField.setFocused(false);
            return;
        }
        int x = mouseX - guiLeft;
        int y = mouseY - guiTop;
        updateGhostPositions();
        int ghost = ghostAt(x, y);
        if (ghost >= 0) {
            if (mouseButton == 1) {
                draft.setInventorySlotContents(ghost, null);
                updateGhostPositions();
            } else if (mouseButton == 0 && drag.active() && acceptBrowserDrop(mouseX, mouseY, drag.stack())) {
                clearDrag();
            }
            return;
        }
        int inventory = inventoryAt(x, y);
        if (mouseButton == 0) {
            ItemStack source = inventory >= 0 ? playerStack(inventory) : browser.stackAt(this, mouseX, mouseY);
            if (source != null && source.getItem() != null) {
                drag.begin(iconCopy(source), mouseX, mouseY);
                gestureStartedOnPress = true;
                return;
            }
        }
        if (inventory >= 0 || mouseButton != 0) return;
        pressButton(mouseX, mouseY);
    }

    private void pressButton(int mouseX, int mouseY) {
        for (Object value : buttonList) {
            GuiButton button = (GuiButton) value;
            if (button.mousePressed(mc, mouseX, mouseY)) {
                pressedButton = button;
                button.func_146113_a(mc.getSoundHandler());
                actionPerformed(button);
                break;
            }
        }
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int button, long elapsed) {
        if (!placementMode && button == 0 && gestureStartedOnPress) gestureMoved |= drag.movedTo(mouseX, mouseY);
    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int button) {
        if (button != 0) return;
        if (pressedButton != null) {
            pressedButton.mouseReleased(mouseX, mouseY);
            pressedButton = null;
        }
        if (!placementMode && gestureStartedOnPress && drag.active()) {
            boolean moved = gestureMoved || drag.movedTo(mouseX, mouseY);
            if (acceptBrowserDrop(mouseX, mouseY, drag.stack()) || moved) clearDrag();
            // A stationary source click keeps the copy on the cursor for click-to-place.
        }
        gestureStartedOnPress = false;
        gestureMoved = false;
    }

    @Override
    protected void handleMouseClick(Slot slot, int slotId, int mouseButton, int mode) {
        // Never dispatch a vanilla window click, even if another GUI hook calls this method.
    }

    @Override
    public void onGuiClosed() {
        try {
            browser.close(this);
        } finally {
            clearLocalInput();
            if (repeatStateSaved) {
                Keyboard.enableRepeatEvents(previousRepeatState);
                repeatStateSaved = false;
            }
        }
    }

    public int getPanelLeft() {
        return guiLeft;
    }

    public int getPanelTop() {
        return guiTop;
    }

    public int getPanelWidth() {
        return xSize;
    }

    public int getPanelHeight() {
        return ySize;
    }

    public boolean isTitleFocused() {
        return !placementMode && titleField != null && titleField.isFocused();
    }

    public boolean acceptBrowserDrop(int screenX, int screenY, ItemStack stack) {
        if (!checkContext() || placementMode || stack == null || stack.getItem() == null) return false;
        updateGhostPositions();
        int ghost = ghostAt(screenX - guiLeft, screenY - guiTop);
        if (ghost < 0) return false;
        draft.setInventorySlotContents(ghost, iconCopy(stack));
        updateGhostPositions();
        return true;
    }

    private boolean checkContext() {
        if (mc != null && mc.currentScreen == this && mc.theWorld != null && mc.theWorld == draft.getWorld()
            && mc.thePlayer != null && mc.thePlayer == contextPlayer
            && mc.thePlayer.isEntityAlive() && !mc.thePlayer.isDead) return true;
        clearLocalInput();
        if (mc != null && mc.currentScreen == this) mc.displayGuiScreen(null);
        return false;
    }

    private void clearDrag() {
        drag.clear();
        gestureStartedOnPress = false;
        gestureMoved = false;
    }

    private void clearLocalInput() {
        clearDrag();
        pressedButton = null;
        pressedMouseButton = -1;
        lastMousePress = 0;
        touchPressCount = 0;
    }

    private static ItemBrowser createBrowser() {
        if (!Loader.isModLoaded("NotEnoughItems")) return ItemBrowser.NONE;
        try {
            return Class.forName("dev.factoryholograms.client.nei.NeiItemBrowser")
                .asSubclass(ItemBrowser.class).getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException | LinkageError | ClassCastException failure) {
            if (!browserFailureLogged && FactoryHolograms.log != null) {
                browserFailureLogged = true;
                FactoryHolograms.log.warn("NEI item browsing is unavailable; using inventory-only icon copies", failure);
            }
            return ItemBrowser.NONE;
        }
    }

    /** No player slots, cursor ownership, close effects, or server-side counterpart. */
    public static final class LocalDisplayContainer extends Container {

        @Override
        public boolean canInteractWith(EntityPlayer player) {
            return true;
        }

        @Override
        public ItemStack slotClick(int slotId, int button, int mode, EntityPlayer player) {
            return null;
        }

        @Override
        public ItemStack transferStackInSlot(EntityPlayer player, int index) {
            return null;
        }

        @Override
        public void onContainerClosed(EntityPlayer player) {}
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {}

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (!checkContext()) return;
        updateGhostPositions();
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();
        try {
            if (!placementMode) browser.beforeDraw(this);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glColor4f(1, 1, 1, 1);
            if (!placementMode) drawDefaultBackground();
            drawPanel();
            drawLabels();
            if (!placementMode) titleField.drawTextBox();
            for (Object value : buttonList) ((GuiButton) value).drawButton(mc, mouseX, mouseY);
            if (!placementMode) browser.draw(this, mouseX, mouseY);
            if (!placementMode && drag.active()) {
                GL11.glPushMatrix();
                try {
                    GL11.glTranslatef(0, 0, 200);
                    drawItem(drag.stack(), mouseX - 8, mouseY - 8);
                } finally {
                    GL11.glPopMatrix();
                }
            } else {
                drawTooltips(mouseX, mouseY);
                if (!placementMode) browser.drawTooltips(this, mouseX, mouseY);
            }
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    private void updateGhostPositions() {
        Arrays.fill(ghostX, -10000);
        Arrays.fill(ghostY, -10000);
        for (int side = 0; side < 2; side++) {
            int[] occupied = draft.occupiedSlots(side == 1);
            for (int ordinal = 0; ordinal < occupied.length; ordinal++) positionGhost(occupied[ordinal], ordinal);
            for (int index = side * LabelRules.ICONS_PER_SIDE; index < (side + 1) * LabelRules.ICONS_PER_SIDE; index++) {
                if (draft.getStackInSlot(index) == null) {
                    positionGhost(index, occupied.length);
                    break;
                }
            }
        }
    }

    private void positionGhost(int index, int ordinal) {
        ghostX[index] = (index < LabelRules.ICONS_PER_SIDE ? 8 : 96) + ordinal % LabelRules.ICONS_PER_ROW * 18;
        ghostY[index] = GHOST_Y + ordinal / LabelRules.ICONS_PER_ROW * 18;
    }

    private int ghostAt(int x, int y) {
        for (int index = 0; index < LabelRules.ICON_SLOTS; index++) {
            if (draft.isSlotVisible(index) && insideSlot(x, y, ghostX[index], ghostY[index])) return index;
        }
        return -1;
    }

    private int inventoryAt(int x, int y) {
        for (int index = 0; index < 36; index++) {
            if (insideSlot(x, y, inventoryX(index), inventoryY(index))) return index;
        }
        return -1;
    }

    private static boolean insideSlot(int x, int y, int slotX, int slotY) {
        return x >= slotX && x < slotX + 16 && y >= slotY && y < slotY + 16;
    }

    private static int inventoryX(int index) {
        return 8 + index % 9 * 18;
    }

    private static int inventoryY(int index) {
        return index < 9 ? HOTBAR_Y : INVENTORY_Y + (index / 9 - 1) * 18;
    }

    private ItemStack playerStack(int index) {
        return mc.thePlayer == null ? null : mc.thePlayer.inventory.getStackInSlot(index);
    }

    private static ItemStack iconCopy(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return null;
        ItemStack copy = stack.copy();
        copy.stackSize = 1;
        return copy;
    }

    private void drawPanel() {
        int x = guiLeft;
        int y = guiTop;
        if (placementMode) {
            drawRect(x + 184, y, x + 296, y + 210, 0xFF102129);
            drawRect(x + 185, y + 1, x + 295, y + 209, 0xFF192F38);
            drawRect(x + 185, y + 1, x + 295, y + 3, 0xFF65E5ED);
            return;
        }
        drawRect(x, y, x + xSize, y + ySize, 0xFF102129);
        drawRect(x + 1, y + 1, x + xSize - 1, y + ySize - 1, 0xFF192F38);
        drawRect(x + 1, y + 1, x + xSize - 1, y + 3, 0xFF65E5ED);
        drawRect(x + 176, y + 8, x + 177, y + ySize - 8, 0xFF387681);
        drawRect(x + 7, y + 45, x + 169, y + 128, 0xFF102129);
        for (int index = 0; index < LabelRules.ICON_SLOTS; index++) {
            if (!draft.isSlotVisible(index) || ghostX[index] < 0) continue;
            drawSlot(x + ghostX[index], y + ghostY[index], true);
            drawItem(draft.getStackInSlot(index), x + ghostX[index], y + ghostY[index]);
        }
        for (int index = 0; index < 36; index++) {
            int slotX = x + inventoryX(index);
            int slotY = y + inventoryY(index);
            drawSlot(slotX, slotY, false);
            drawItem(playerStack(index), slotX, slotY);
        }

    }

    private void drawSlot(int x, int y, boolean ghost) {
        drawRect(x - 1, y - 1, x + 17, y + 17, ghost ? 0xFF387681 : 0xFF40525B);
        drawRect(x, y, x + 16, y + 16, 0xFF0B181E);
    }

    private void drawItem(ItemStack stack, int x, int y) {
        if (stack == null) return;
        float previousZ = itemRenderer.zLevel;
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();
        try {
            RenderHelper.enableGUIStandardItemLighting();
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL12.GL_RESCALE_NORMAL);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glColor4f(1, 1, 1, 1);
            itemRenderer.zLevel = 100;
            // The normal GUI entry point includes Forge's inventory-renderer hook and enchantment effects.
            itemRenderer.renderItemAndEffectIntoGUI(fontRendererObj, mc.getTextureManager(), stack, x, y);
            itemRenderer.renderItemOverlayIntoGUI(fontRendererObj, mc.getTextureManager(), stack, x, y);
        } finally {
            itemRenderer.zLevel = previousZ;
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    private void drawLabels() {
        if (!placementMode) {
            drawLabel(text("title", "Factory Holograms"), 10, 9, ACCENT);
            drawLabel(text("input", "Input"), 8, 46, ACCENT);
            drawLabel(text("output", "Output"), 96, 46, ACCENT);
            drawLabel("->", 81, 84, ACCENT);
        }
        drawLabel(fontRendererObj.trimStringToWidth(rotationMode ? text("rotation.title", "Rotation / tilt")
            : text("position.title", "Position"), 112), 184, 10, ACCENT);
        String[] axes = rotationMode ? new String[] { "Y", "P" } : new String[] { "X", "Y", "Z" };
        float[] position = rotationMode ? new float[] {draft.getYaw(), draft.getPitch()} : draft.getPositionOffsets();
        for (int axis = 0; axis < position.length; axis++) {
            int y = 37 + axis * 26;
            drawLabel(axes[axis], 184, y, ACCENT);
            String value = String.format(Locale.ROOT, rotationMode ? "%+.0f°" : "%+.2f", position[axis]);
            drawLabel(value, 247 - fontRendererObj.getStringWidth(value) / 2, y, 0xE4F8FA);
        }
        if (rotationMode && HologramConfig.autoRotate) {
            drawLabel(fontRendererObj.trimStringToWidth(text("rotation.auto", "Auto-rotate on"), 112), 184, 94, 0xB7C9CE);
        }
        drawLabel(fontRendererObj.trimStringToWidth(rotationMode ? text("rotation.step", "5° (Shift: 15°)")
            : text("position.step", "0.25 (Shift: 1)"), 112),
            184, placementMode ? 22 : 137, 0xB7C9CE);
        if (!placementMode) {
            drawLabel(StatCollector.translateToLocal("container.inventory"), 8, INVENTORY_Y - 10, 0xB7C9CE);
            drawLabel(fontRendererObj.trimStringToWidth(text("ghost.drag.short", "Hover icons for help"), 112),
                184, 223, 0xB7C9CE);
        }
    }

    private void drawLabel(String value, int x, int y, int color) {
        fontRendererObj.drawString(value, guiLeft + x, guiTop + y, color);
    }

    private void drawTooltips(int mouseX, int mouseY) {
        int x = mouseX - guiLeft;
        int y = mouseY - guiTop;
        int ghost = placementMode ? -1 : ghostAt(x, y);
        int inventory = placementMode ? -1 : inventoryAt(x, y);
        int modeY = placementMode ? 140 : 172;
        List<String> lines = new ArrayList<String>();
        if (ghost >= 0) {
            addItemTooltip(lines, draft.getStackInSlot(ghost));
            lines.add(sourceHint());
            lines.add(text("ghost.drag.place", "Left-click with a copied item, or drag it here."));
            lines.add(text("ghost.clear", "Right-click: clear icon"));
            lines.add(text("ghost.limit", "16 per side, 4 per row; filling a slot reveals the next."));
            lines.add(text("ghost.immediate", "All changes saved by Apply"));
            lines.add(text("ghost.drag.cancel", "Escape cancels a copy first; otherwise discards edits."));
        } else if (inventory >= 0) {
            addItemTooltip(lines, playerStack(inventory));
            lines.add(sourceHint());
        } else if (x >= 184 && x < 296) {
            if (y >= modeY && y < modeY + 18) {
                lines.add(placementMode
                    ? text("position.back.hint", "Return to title and icon editing without saving.")
                    : text("position.preview.hint", "Position the live preview with the world unobstructed."));
            } else if (!placementMode && y >= 223 && y < 236) {
                lines.add(sourceHint());
            } else if (!placementMode && y >= 148 && y < 166) {
                lines.add(text("display.hint", "Only changes visibility on your client."));
            } else if (y >= 32 && y < 110) {
                if (rotationMode) {
                    lines.add(text("rotation.hint", "Y: yaw, P: pitch. Disable auto-rotate in the mod settings to edit angles."));
                } else {
                    String[] axes = { "x", "y", "z" };
                    lines.add(text("position." + axes[(y - 32) / 26], "World axis"));
                }
            }
            if ((y >= 32 && y < 134) || (y >= modeY && y < modeY + 18)) {
                lines.add(text("position.live.hint", "Preview updates immediately. Apply saves; Escape discards edits."));
            }
        }
        if (!lines.isEmpty()) {
            List<String> wrapped = new ArrayList<String>();
            for (String line : lines) {
                wrapped.addAll(fontRendererObj.listFormattedStringToWidth(line, Math.max(1, Math.min(250, width - 24))));
            }
            drawHoveringText(wrapped, mouseX, mouseY, fontRendererObj);
        }
    }

    private void addItemTooltip(List<String> lines, ItemStack stack) {
        if (stack == null || mc.thePlayer == null) return;
        List<String> tooltip = stack.getTooltip(mc.thePlayer, mc.gameSettings.advancedItemTooltips);
        for (int line = 0; line < tooltip.size(); line++) {
            lines.add((line == 0 ? stack.getRarity().rarityColor : EnumChatFormatting.GRAY) + tooltip.get(line));
        }
    }

    private static String sourceHint() {
        return text("ghost.drag.hint", "Drag or click inventory/NEI, then a ghost slot. Esc cancels the copy. Real items stay put.");
    }

    private static String text(String key, String fallback, Object... arguments) {
        String fullKey = "gui.factoryholograms." + key;
        return StatCollector.canTranslate(fullKey)
            ? StatCollector.translateToLocalFormatted(fullKey, arguments)
            : String.format(Locale.ROOT, fallback, arguments);
    }
}
