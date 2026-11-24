package dev.factoryholograms.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;
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
    private static final int RECIPE_PREV = 20;
    private static final int RECIPE_NEXT = 21;
    private static final int RECIPE_ADD = 22;
    private static final int RECIPE_REMOVE = 23;
    private static final int SELECTOR_Y = 43;
    private static final int GHOST_Y = 60;
    private static final int ACTION_Y = GHOST_Y + 78;
    private static final int INVENTORY_Y = 172;
    private static final int HOTBAR_Y = 230;
    private static final int TITLE_Y = 6;
    private static final int TITLE_FIELD_Y = 18;
    private static final int ROW_Y = 22;
    private static final int ROW_STEP = 24;
    private static final int AUTO_HINT_Y = 70;
    private static final int RESET_Y = 96;
    private static final int STEP_HINT_Y = 118;
    private static final int DISPLAY_Y = 138;
    private static final int PREVIEW_Y = 171;
    private static final int ROTATION_Y = 195;
    private static final int HOVER_HINT_Y = 234;
    private static final int PLACEMENT_BACK_Y = 140;
    private static final int PLACEMENT_APPLY_Y = 164;
    private static final int PLACEMENT_ROTATION_Y = 188;
    private static final int PLACEMENT_HEIGHT = 214;

    private static final ResourceLocation PANEL_TEXTURE = new ResourceLocation("textures/gui/demo_background.png");
    private static final ResourceLocation SLOT_TEXTURE = new ResourceLocation("textures/gui/container/generic_54.png");

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
    private GuiTextField recipeField;
    private GuiButton displayButton;
    private boolean repeatStateSaved;
    private boolean previousRepeatState;
    private boolean placementMode;
    private boolean rotationMode;
    private int currentRecipe;

    public HologramGui(HologramLabel draft, ClientProxy owner) {
        super(new LocalDisplayContainer());
        xSize = 304;
        ySize = 254;
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
        guiTop = placementMode ? Math.max(0, height - PLACEMENT_HEIGHT - 8) : (height - ySize) / 2;
        if (!repeatStateSaved) {
            previousRepeatState = Keyboard.areRepeatEventsEnabled();
            repeatStateSaved = true;
        }
        Keyboard.enableRepeatEvents(true);
        // Preserve the draft title on resize, but never carry an unfinished drag across layouts.
        String title = titleField == null ? draft.getTitle() : titleField.getText();
        boolean focused = !placementMode && (titleField == null || titleField.isFocused());
        titleField = new GuiTextField(fontRendererObj, guiLeft + 9, guiTop + TITLE_FIELD_Y, 158, 18);
        titleField.setMaxStringLength(LabelRules.MAX_TITLE);
        titleField.setText(title == null ? "" : title);
        titleField.setFocused(focused);
        boolean recipeFocused = !placementMode && recipeField != null && recipeField.isFocused();
        recipeField = new GuiTextField(fontRendererObj, guiLeft + 22, guiTop + SELECTOR_Y, 82, 12);
        recipeField.setMaxStringLength(LabelRules.MAX_RECIPE_NAME);
        recipeField.setFocused(recipeFocused && !focused);
        currentRecipe = Math.max(0, Math.min(currentRecipe, draft.getRecipeCount() - 1));
        loadRecipeField();

        buttonList.clear();
        displayButton = null;
        if (placementMode) {
            buttonList.add(new VanillaButton(APPLY, guiLeft + 184, guiTop + PLACEMENT_APPLY_Y, 112, 18, text("apply", "Apply")));
        } else {
            displayButton = new VanillaButton(TOGGLE, guiLeft + 184, guiTop + DISPLAY_Y, 112, 18, "");
            buttonList.add(displayButton);
            buttonList.add(new VanillaButton(APPLY, guiLeft + 8, guiTop + ACTION_Y, 78, 18, text("apply", "Apply")));
            buttonList.add(new VanillaButton(DELETE, guiLeft + 90, guiTop + ACTION_Y, 78, 18, text("delete", "Delete")));
            buttonList.add(new VanillaButton(RECIPE_PREV, guiLeft + 8, guiTop + SELECTOR_Y, 12, 12, "<"));
            buttonList.add(new VanillaButton(RECIPE_NEXT, guiLeft + 106, guiTop + SELECTOR_Y, 12, 12, ">"));
            buttonList.add(new VanillaButton(RECIPE_ADD, guiLeft + 142, guiTop + SELECTOR_Y, 12, 12, "+"));
            buttonList.add(new VanillaButton(RECIPE_REMOVE, guiLeft + 156, guiTop + SELECTOR_Y, 12, 12, "-"));
        }
        for (int axis = 0; axis < (rotationMode ? 2 : 3); axis++) {
            int y = guiTop + ROW_Y + axis * ROW_STEP;
            buttonList.add(new VanillaButton(POSITION_FIRST + axis * 2, guiLeft + 198, y, 18, 18, "-"));
            buttonList.add(new VanillaButton(POSITION_FIRST + axis * 2 + 1, guiLeft + 278, y, 18, 18, "+"));
        }
        buttonList.add(new VanillaButton(POSITION_RESET, guiLeft + 184, guiTop + RESET_Y, 112, 18,
            rotationMode ? text("rotation.reset", "Reset angles") : text("position.reset", "Reset position")));

        buttonList.add(new VanillaButton(POSITION_MODE, guiLeft + 184, guiTop + (placementMode ? PLACEMENT_BACK_Y : PREVIEW_Y), 112, 18,
            placementMode ? text("position.back", "Back to editor") : text("position.preview", "Position preview")));
        buttonList.add(new VanillaButton(ROTATION_MODE, guiLeft + 184, guiTop + (placementMode ? PLACEMENT_ROTATION_Y : ROTATION_Y), 112, 18,
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
        recipeField.updateCursorCounter();
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
        } else if (button.id == RECIPE_PREV) {
            currentRecipe = Math.max(0, currentRecipe - 1);
            loadRecipeField();
        } else if (button.id == RECIPE_NEXT) {
            currentRecipe = Math.min(draft.getRecipeCount() - 1, currentRecipe + 1);
            loadRecipeField();
        } else if (button.id == RECIPE_ADD) {
            int added = draft.addRecipe();
            if (added >= 0) currentRecipe = added;
            loadRecipeField();
        } else if (button.id == RECIPE_REMOVE) {
            draft.removeRecipe(currentRecipe);
            currentRecipe = Math.max(0, Math.min(currentRecipe, draft.getRecipeCount() - 1));
            loadRecipeField();
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

    private void loadRecipeField() {
        recipeField.setText(draft.getRecipeName(currentRecipe));
        recipeField.setCursorPositionEnd();
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
            } else if (button.id == RECIPE_PREV) {
                button.enabled = currentRecipe > 0;
            } else if (button.id == RECIPE_NEXT) {
                button.enabled = currentRecipe < draft.getRecipeCount() - 1;
            } else if (button.id == RECIPE_ADD) {
                button.enabled = draft.getRecipeCount() < LabelRules.MAX_RECIPES;
            } else if (button.id == RECIPE_REMOVE) {
                button.enabled = !(draft.getRecipeCount() == 1 && draft.isRecipeEmpty(0));
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
            boolean toRecipe = titleField.isFocused();
            titleField.setFocused(recipeField.isFocused() || !titleField.isFocused());
            recipeField.setFocused(toRecipe);
            return;
        }
        if (titleField.isFocused()) {
            // Inventory and hotbar keys are ordinary text here, never inventory actions.
            titleField.textboxKeyTyped(character, keyCode);
            return;
        }
        if (recipeField.isFocused()) {
            recipeField.textboxKeyTyped(character, keyCode);
            draft.setRecipeName(currentRecipe, recipeField.getText());
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
        recipeField.mouseClicked(mouseX, mouseY, mouseButton);
        if (consumed) {
            titleField.setFocused(false);
            recipeField.setFocused(false);
            return;
        }
        if (mouseButton == 0 && drag.active() && (inTitleField(mouseX, mouseY) || inRecipeField(mouseX, mouseY))) {
            acceptBrowserDrop(mouseX, mouseY, drag.stack());
            clearDrag();
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
        return !placementMode && ((titleField != null && titleField.isFocused()) || (recipeField != null && recipeField.isFocused()));
    }

    public boolean acceptBrowserDrop(int screenX, int screenY, ItemStack stack) {
        if (!checkContext() || placementMode || stack == null || stack.getItem() == null) return false;
        if (inTitleField(screenX, screenY)) {
            String name;
            try {
                name = stack.getDisplayName();
            } catch (RuntimeException e) {
                return false;
            }
            if (name == null) return false;
            titleField.setText(name); // draft label is read from titleField on save, so no extra sync is needed
            titleField.setCursorPositionEnd();
            titleField.setFocused(true);
            return true;
        }
        if (inRecipeField(screenX, screenY)) {
            String name;
            try {
                name = stack.getDisplayName();
            } catch (RuntimeException e) {
                return false;
            }
            if (name == null) return false;
            draft.setRecipeName(currentRecipe, name);
            loadRecipeField();
            titleField.setFocused(false);
            recipeField.setFocused(true);
            return true;
        }
        updateGhostPositions();
        int ghost = ghostAt(screenX - guiLeft, screenY - guiTop);
        if (ghost < 0) return false;
        draft.setInventorySlotContents(ghost, iconCopy(stack));
        updateGhostPositions();
        return true;
    }

    private boolean inRecipeField(int screenX, int screenY) {
        return recipeField != null && screenX >= recipeField.xPosition && screenX < recipeField.xPosition + recipeField.width
            && screenY >= recipeField.yPosition && screenY < recipeField.yPosition + recipeField.height;
    }

    private boolean inTitleField(int screenX, int screenY) {
        return titleField != null && screenX >= titleField.xPosition && screenX < titleField.xPosition + titleField.width
            && screenY >= titleField.yPosition && screenY < titleField.yPosition + titleField.height;
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
            if (!placementMode) {
                titleField.drawTextBox();
                recipeField.drawTextBox();
                if (recipeField.getText().isEmpty() && !recipeField.isFocused()) {
                    fontRendererObj.drawString(text("recipe.placeholder", "Recipe %d", currentRecipe + 1),
                        recipeField.xPosition + 4, recipeField.yPosition + 2, 0x707070);
                }
            }
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
        currentRecipe = Math.max(0, Math.min(currentRecipe, draft.getRecipeCount() - 1));
        for (int side = 0; side < 2; side++) {
            int[] occupied = draft.occupiedSlots(currentRecipe, side == 1);
            for (int ordinal = 0; ordinal < occupied.length; ordinal++) positionGhost(occupied[ordinal], ordinal);
            int start = LabelRules.slot(currentRecipe, side * LabelRules.ICONS_PER_SIDE);
            for (int index = start; index < start + LabelRules.ICONS_PER_SIDE; index++) {
                if (draft.getStackInSlot(index) == null) {
                    positionGhost(index, occupied.length);
                    break;
                }
            }
        }
    }

    private void positionGhost(int index, int ordinal) {
        ghostX[index] = (LabelRules.isInputSlot(index) ? 8 : 96) + ordinal % LabelRules.ICONS_PER_ROW * 18;
        ghostY[index] = GHOST_Y + ordinal / LabelRules.ICONS_PER_ROW * 18;
    }

    private int ghostAt(int x, int y) {
        int start = LabelRules.slot(currentRecipe, 0);
        for (int index = start; index < start + LabelRules.SLOTS_PER_RECIPE; index++) {
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
            drawNineSlice(x + 176, y, 128, PLACEMENT_HEIGHT);
            return;
        }
        drawNineSlice(x, y, xSize, ySize);
        drawRect(x + 176, y + 6, x + 177, y + ySize - 6, 0x60000000);
        drawRect(x + 177, y + 6, x + 178, y + ySize - 6, 0x70FFFFFF);
        drawInset(x + 5, y + GHOST_Y - 3, x + 170, y + GHOST_Y + 74);
        int firstGhost = LabelRules.slot(currentRecipe, 0);
        for (int index = firstGhost; index < firstGhost + LabelRules.SLOTS_PER_RECIPE; index++) {
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

    /** Theme-adaptive sunken area built from translucent overlays. */
    private static void drawInset(int x1, int y1, int x2, int y2) {
        drawRect(x1, y1, x2, y2, 0x28000000);
        drawRect(x1, y1, x2, y1 + 1, 0x60000000);
        drawRect(x1, y1 + 1, x1 + 1, y2, 0x60000000);
        drawRect(x1 + 1, y2 - 1, x2, y2, 0x70FFFFFF);
        drawRect(x2 - 1, y1 + 1, x2, y2 - 1, 0x70FFFFFF);
    }

    private void drawNineSlice(int x, int y, int w, int h) {
        mc.getTextureManager().bindTexture(PANEL_TEXTURE);
        prepareTextureDraw();
        final int b = 4;
        final int tw = 248;
        final int th = 166;
        int mw = w - 2 * b;
        int mh = h - 2 * b;
        drawTexturedRect(x, y, b, b, 0, 0, b, b);
        drawTexturedRect(x + b, y, mw, b, b, 0, tw - 2 * b, b);
        drawTexturedRect(x + w - b, y, b, b, tw - b, 0, b, b);
        drawTexturedRect(x, y + b, b, mh, 0, b, b, th - 2 * b);
        drawTexturedRect(x + b, y + b, mw, mh, b, b, tw - 2 * b, th - 2 * b);
        drawTexturedRect(x + w - b, y + b, b, mh, tw - b, b, b, th - 2 * b);
        drawTexturedRect(x, y + h - b, b, b, 0, th - b, b, b);
        drawTexturedRect(x + b, y + h - b, mw, b, b, th - b, tw - 2 * b, b);
        drawTexturedRect(x + w - b, y + h - b, b, b, tw - b, th - b, b, b);
    }

    private static void prepareTextureDraw() {
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(1, 1, 1, 1);
    }

    /** Draws a region of a 256x256 texture, stretched to the destination rectangle. */
    private static void drawTexturedRect(int x, int y, int w, int h, int u, int v, int uw, int vh) {
        float scale = 1.0F / 256.0F;
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        t.addVertexWithUV(x, y + h, 0, u * scale, (v + vh) * scale);
        t.addVertexWithUV(x + w, y + h, 0, (u + uw) * scale, (v + vh) * scale);
        t.addVertexWithUV(x + w, y, 0, (u + uw) * scale, v * scale);
        t.addVertexWithUV(x, y, 0, u * scale, v * scale);
        t.draw();
    }

    private void drawSlot(int x, int y, boolean ghost) {
        mc.getTextureManager().bindTexture(SLOT_TEXTURE);
        prepareTextureDraw();
        drawTexturedRect(x - 1, y - 1, 18, 18, 7, 17, 18, 18);
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
            drawLabel(text("title", "Factory Holograms"), 8, TITLE_Y, GuiTheme.label());
            drawLabel((currentRecipe + 1) + "/" + draft.getRecipeCount(), 121, SELECTOR_Y + 2, GuiTheme.label());
            drawLabel("->", 87 - fontRendererObj.getStringWidth("->") / 2, GHOST_Y + 32, GuiTheme.label());
        }
        drawLabel(fontRendererObj.trimStringToWidth(rotationMode ? text("rotation.title", "Rotation / tilt")
            : text("position.title", "Position"), 112), 184, TITLE_Y, GuiTheme.label());
        String[] axes = rotationMode ? new String[] { "Y", "P" } : new String[] { "X", "Y", "Z" };
        float[] position = rotationMode ? new float[] {draft.getYaw(), draft.getPitch()} : draft.getPositionOffsets();
        for (int axis = 0; axis < position.length; axis++) {
            int y = ROW_Y + axis * ROW_STEP + 5;
            drawLabel(axes[axis], 184, y, GuiTheme.label());
            String value = String.format(Locale.ROOT, rotationMode ? "%+.0f\u00b0" : "%+.2f", position[axis]);
            drawLabel(value, 247 - fontRendererObj.getStringWidth(value) / 2, y, GuiTheme.label());
        }
        if (rotationMode && HologramConfig.autoRotate) {
            drawLabel(fontRendererObj.trimStringToWidth(text("rotation.auto", "Auto-rotate on"), 112), 184, AUTO_HINT_Y, GuiTheme.hint());
        }
        drawLabel(fontRendererObj.trimStringToWidth(rotationMode ? text("rotation.step", "5\u00b0 (Shift: 15\u00b0)")
            : text("position.step", "0.25 (Shift: 1)"), 112),
            184, STEP_HINT_Y, GuiTheme.hint());
        if (!placementMode) {
            drawLabel(StatCollector.translateToLocal("container.inventory"), 8, INVENTORY_Y - 11, GuiTheme.label());
            drawLabel(fontRendererObj.trimStringToWidth(text("ghost.drag.short", "Hover icons for help"), 112),
                184, HOVER_HINT_Y, GuiTheme.hint());
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
        int modeY = placementMode ? PLACEMENT_BACK_Y : PREVIEW_Y;
        List<String> lines = new ArrayList<String>();
        if (ghost >= 0) {
            addItemTooltip(lines, draft.getStackInSlot(ghost));
            lines.add(sourceHint());
            lines.add(text("ghost.drag.place", "Left-click with a copied item, or drag it here."));
            lines.add(text("ghost.clear", "Right-click: clear icon"));
            lines.add(text("ghost.limit", "16 per side, up to 8 recipes; filling a slot reveals the next."));
            lines.add(text("ghost.immediate", "All changes saved by Apply"));
            lines.add(text("ghost.drag.cancel", "Escape cancels a copy first; otherwise discards edits."));
        } else if (inventory >= 0) {
            addItemTooltip(lines, playerStack(inventory));
            lines.add(sourceHint());
        } else if (!placementMode && recipeButtonKey(x, y) != null) {
            lines.add(text(recipeButtonKey(x, y), "Recipe"));
        } else if (x >= 184 && x < 296) {
            if (y >= modeY && y < modeY + 18) {
                lines.add(placementMode
                    ? text("position.back.hint", "Return to title and icon editing without saving.")
                    : text("position.preview.hint", "Position the live preview with the world unobstructed."));
            } else if (!placementMode && y >= HOVER_HINT_Y && y < HOVER_HINT_Y + 8) {
                lines.add(sourceHint());
            } else if (!placementMode && y >= DISPLAY_Y && y < DISPLAY_Y + 18) {
                lines.add(text("display.hint", "Only changes visibility on your client."));
            } else if (y >= ROW_Y && y < ROW_Y + (rotationMode ? 2 : 3) * ROW_STEP) {
                if (rotationMode) {
                    lines.add(text("rotation.hint", "Y: yaw, P: pitch. Disable auto-rotate in the mod settings to edit angles."));
                } else {
                    String[] axes = { "x", "y", "z" };
                    lines.add(text("position." + axes[Math.min(2, (y - ROW_Y) / ROW_STEP)], "World axis"));
                }
            }
            if ((y >= ROW_Y && y < RESET_Y + 18) || (y >= modeY && y < modeY + 18)) {
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

    private String recipeButtonKey(int x, int y) {
        if (y < SELECTOR_Y || y >= SELECTOR_Y + 12) return null;
        if (x >= 8 && x < 20) return "recipe.prev";
        if (x >= 22 && x < 104) return "recipe.name";
        if (x >= 106 && x < 118) return "recipe.next";
        if (x >= 142 && x < 154) return "recipe.add";
        if (x >= 156 && x < 168) return "recipe.remove";
        return null;
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

    /** GuiButton that keeps its bottom border at any height by drawing the texture in four quadrants. */
    private static final class VanillaButton extends GuiButton {

        VanillaButton(int id, int x, int y, int width, int height, String label) {
            super(id, x, y, width, height, label);
        }

        @Override
        public void drawButton(Minecraft mc, int mouseX, int mouseY) {
            if (!visible) return;
            FontRenderer font = mc.fontRenderer;
            mc.getTextureManager().bindTexture(buttonTextures);
            GL11.glColor4f(1, 1, 1, 1);
            field_146123_n = mouseX >= xPosition && mouseY >= yPosition
                && mouseX < xPosition + width && mouseY < yPosition + height;
            int state = getHoverState(field_146123_n);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            int v = 46 + state * 20;
            int halfW = width / 2;
            int topH = height / 2;
            int bottomH = height - topH;
            int bottomV = v + 20 - bottomH;
            drawTexturedModalRect(xPosition, yPosition, 0, v, halfW, topH);
            drawTexturedModalRect(xPosition + halfW, yPosition, 200 - halfW, v, halfW, topH);
            drawTexturedModalRect(xPosition, yPosition + topH, 0, bottomV, halfW, bottomH);
            drawTexturedModalRect(xPosition + halfW, yPosition + topH, 200 - halfW, bottomV, halfW, bottomH);
            mouseDragged(mc, mouseX, mouseY);
            int color = 14737632;
            if (packedFGColour != 0) color = packedFGColour;
            else if (!enabled) color = 10526880;
            else if (field_146123_n) color = 16777120;
            drawCenteredString(font, displayString, xPosition + width / 2, yPosition + (height - 8) / 2, color);
        }
    }
}
