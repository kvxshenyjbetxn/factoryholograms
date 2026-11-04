package dev.factoryholograms.client;

import java.nio.FloatBuffer;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.TextureMap;

import net.minecraft.item.ItemStack;

import net.minecraft.util.StatCollector;
import net.minecraftforge.client.ForgeHooksClient;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import dev.factoryholograms.HologramConfig;
import dev.factoryholograms.HologramLabel;
import dev.factoryholograms.HologramLayout;
import dev.factoryholograms.HologramPalette;
import dev.factoryholograms.HologramPick;
import dev.factoryholograms.HologramPlacement;


@SideOnly(Side.CLIENT)
public final class HologramRenderer {

    private static final int MAX_TITLE_WIDTH = 160;
    private static final float PIXEL_SCALE = 0.0125F;

    // Do not change the shared GUI RenderItem's zLevel or render flags.
    private final RenderItem itemRenderer = new RenderItem();
    private final RenderBlocks blockRenderer = new RenderBlocks();
    private final FloatBuffer fadeColor = BufferUtils.createFloatBuffer(4);

    public void render(HologramLabel label, double cameraX, double cameraY, double cameraZ, float eyeHeight) {
        render(label, cameraX, cameraY, cameraZ, eyeHeight, false, -1);
    }

    public void render(HologramLabel label, double cameraX, double cameraY, double cameraZ, float eyeHeight, boolean preview) {
        render(label, cameraX, cameraY, cameraZ, eyeHeight, preview, -1);
    }

    /** Fade in [0, 1]; 0 when out of range or invisible. */
    private static float visibleFade(double distanceSquared, boolean preview) {
        int range = Math.max(4, Math.min(64, HologramConfig.renderDistance));
        if (!preview && distanceSquared >= range * range) return 0.0F;
        double fadeRange = Math.min(4.0D, range * 0.25D);
        float opacity = Math.max(0.0F, Math.min(1.0F, HologramConfig.opacity));
        // An edited draft remains findable even outside display range or at zero global opacity.
        float fade = preview ? Math.max(0.35F, opacity)
            : (float) Math.min(1.0D, (range - Math.sqrt(distanceSquared)) / fadeRange) * opacity;
        return fade <= 0.003F ? 0.0F : fade;
    }

    private static HologramLayout layoutFor(HologramLabel label, FontRenderer font) {
        int[] recipes = label.visibleRecipes();
        int[][] inputs = new int[recipes.length][];
        int[][] outputs = new int[recipes.length][];
        int[] nameWidths = new int[recipes.length];
        for (int i = 0; i < recipes.length; i++) {
            inputs[i] = label.occupiedSlots(recipes[i], false);
            outputs[i] = label.occupiedSlots(recipes[i], true);
            nameWidths[i] = font.getStringWidth(label.getRecipeName(recipes[i]));
        }
        return new HologramLayout(inputs, outputs, nameWidths, font.getStringWidth(titleFor(label, font)));
    }

    private static String titleFor(HologramLabel label, FontRenderer font) {
        String title = label.getTitle();
        if (title == null || title.trim().isEmpty()) return "";
        if (font.getStringWidth(title) > MAX_TITLE_WIDTH) {
            title = font.trimStringToWidth(title, MAX_TITLE_WIDTH - font.getStringWidth("...")) + "...";
        }
        return title;
    }

    /** {yaw, pitch} exactly as passed to glRotatef(-yaw, y) then glRotatef(pitch, x). */
    private static float[] rotationFor(Minecraft mc, HologramLabel label) {
        if (HologramConfig.autoRotate) {
            float pitch = RenderManager.instance.playerViewX;
            return new float[] {RenderManager.instance.playerViewY, mc.gameSettings.thirdPersonView == 2 ? -pitch : pitch};
        }
        return new float[] {label.getYaw(), label.getPitch()};
    }

    /** Ray (absolute world coordinates, direction normalized) against one label's icons. */
    public HologramHover pick(HologramLabel label, double ox, double oy, double oz, double dx, double dy, double dz) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!HologramConfig.enabled || mc.gameSettings.hideGUI || mc.theWorld == null
            || label.getWorld() != mc.theWorld || label.isEmpty()) return null;
        double cx = HologramPlacement.centerX(label), cy = HologramPlacement.centerY(label), cz = HologramPlacement.centerZ(label);
        double ex = cx - ox, ey = cy - oy, ez = cz - oz;
        if (visibleFade(ex * ex + ey * ey + ez * ez, false) <= 0.0F) return null;
        HologramLayout layout = layoutFor(label, mc.fontRenderer);
        float[] rotation = rotationFor(mc, label);
        double[] hit = HologramPick.panelHit(ox, oy, oz, dx, dy, dz, cx, cy, cz, rotation[0], rotation[1],
            PIXEL_SCALE, layout.width, layout.height);
        if (hit == null) return null;
        int slot = HologramPick.slotAt(layout, hit[1], hit[2]);
        if (slot < 0) return null;
        ItemStack stack = label.getStackInSlot(slot);
        if (stack == null || stack.getItem() == null) return null;
        return new HologramHover(label, slot, stack, hit[0]);
    }

    /**
     * Distance along the ray to this label's panel (not just an icon), or -1. When recipeOut is non-null,
     * recipeOut[0] receives the real recipe index of the band that was hit, or -1 (title area / between bands).
     */
    public double pickPanel(HologramLabel label, double ox, double oy, double oz, double dx, double dy, double dz,
        int[] recipeOut) {
        if (recipeOut != null) recipeOut[0] = -1;
        Minecraft mc = Minecraft.getMinecraft();
        if (!HologramConfig.enabled || mc.gameSettings.hideGUI || mc.theWorld == null
            || label.getWorld() != mc.theWorld || label.isEmpty()) return -1;
        double cx = HologramPlacement.centerX(label), cy = HologramPlacement.centerY(label), cz = HologramPlacement.centerZ(label);
        double ex = cx - ox, ey = cy - oy, ez = cz - oz;
        if (visibleFade(ex * ex + ey * ey + ez * ez, false) <= 0.0F) return -1;
        HologramLayout layout = layoutFor(label, mc.fontRenderer);
        float[] rotation = rotationFor(mc, label);
        double[] hit = HologramPick.panelHit(ox, oy, oz, dx, dy, dz, cx, cy, cz, rotation[0], rotation[1],
            PIXEL_SCALE, layout.width, layout.height);
        if (hit == null) return -1;
        if (recipeOut != null) {
            int band = layout.bandAt(hit[2]);
            int[] recipes = label.visibleRecipes();
            if (band >= 0 && band < recipes.length) recipeOut[0] = recipes[band];
        }
        return hit[0];
    }

    /** World coordinates of the center of an icon on the label's panel, or null if the slot is not shown. */
    public double[] slotWorldPosition(HologramLabel label, int slot) {
        Minecraft mc = Minecraft.getMinecraft();
        HologramLayout layout = layoutFor(label, mc.fontRenderer);
        if (!layout.hasSlot(slot)) return null;
        float[] rotation = rotationFor(mc, label);
        return HologramPick.pixelToWorld(layout.slotX(slot) + 8, layout.slotY(slot) + 8,
            HologramPlacement.centerX(label), HologramPlacement.centerY(label), HologramPlacement.centerZ(label),
            rotation[0], rotation[1], PIXEL_SCALE, layout.width, layout.height);
    }

    public void render(HologramLabel label, double cameraX, double cameraY, double cameraZ, float eyeHeight,
        boolean preview, int hoveredSlot) {
        Minecraft mc = Minecraft.getMinecraft();
        if ((!HologramConfig.enabled && !preview) || mc.gameSettings.hideGUI || mc.theWorld == null
            || label.getWorld() != mc.theWorld || (label.isEmpty() && !preview)) return;

        // Subtract before entering GL's float matrices, including at distant world coordinates.
        double x = HologramPlacement.centerX(label) - cameraX;
        double y = HologramPlacement.centerY(label) - cameraY;
        double z = HologramPlacement.centerZ(label) - cameraZ;
        double dx = x;
        double dy = y - eyeHeight;
        double dz = z;
        double distanceSquared = dx * dx + dy * dy + dz * dz;
        float fade = visibleFade(distanceSquared, preview);
        if (fade <= 0.0F) return;

        FontRenderer font = mc.fontRenderer;
        String title = titleFor(label, font);
        HologramLayout layout = layoutFor(label, font);
        int previousMatrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        int previousTextureUnit = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        float previousLightX = OpenGlHelper.lastBrightnessX;
        float previousLightY = OpenGlHelper.lastBrightnessY;
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        try {
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0F, 240.0F);
            OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_FOG);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glAlphaFunc(GL11.GL_GREATER, 0.001F);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthFunc(GL11.GL_LEQUAL);
            // Translucent pixels must not occlude later tiles/entities; walls still occlude this panel.
            GL11.glDepthMask(false);

            GL11.glTranslated(x, y, z);
            float[] rotation = rotationFor(mc, label);
            GL11.glRotatef(-rotation[0], 0.0F, 1.0F, 0.0F);
            GL11.glRotatef(rotation[1], 1.0F, 0.0F, 0.0F);
            // Keep GUI item model depth shallow instead of projecting it through nearby walls.
            GL11.glScalef(-PIXEL_SCALE, -PIXEL_SCALE, 0.001F);
            GL11.glTranslatef(-layout.width / 2.0F, -layout.height / 2.0F, 0.0F);

            int color = HologramConfig.color & 0xFFFFFF;
            drawPanel(layout, color, fade);
            setTextureFade(fade);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            int textColor = 0xFF000000 | (HologramConfig.textColor & 0xFFFFFF);
            font.drawString(title, (layout.width - font.getStringWidth(title)) / 2, 5, textColor);
            int[] shownRecipes = label.visibleRecipes();
            int nameColor = 0xFF000000 | brighten(HologramConfig.textColor & 0xFFFFFF, 0.75F);
            for (int b = 0; b < layout.bandCount(); b++) {
                if (layout.hasName(b)) {
                    String name = label.getRecipeName(shownRecipes[b]);
                    font.drawString(name, (layout.width - font.getStringWidth(name)) / 2, layout.nameY(b) + 1, nameColor);
                }
                if (layout.hasArrow(b)) font.drawString("->", layout.arrowX, layout.arrowY(b), textColor);
            }
            int[] shown = layout.allSlots();
            for (int slot : shown) drawItem(mc, label.getStackInSlot(slot), layout.slotX(slot), layout.slotY(slot));
            if (hoveredSlot >= 0 && layout.hasSlot(hoveredSlot)) {
                int slotX = layout.slotX(hoveredSlot);
                int slotY = layout.slotY(hoveredSlot);
                GL11.glDisable(GL11.GL_TEXTURE_2D);
                GL11.glEnable(GL11.GL_BLEND);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                rectangle(slotX, slotY, slotX + 16, slotY + 16, 0xFFFFFF, 0.35F * fade);
                GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            }
        } finally {
            // glPopAttrib alone does not restore OpenGlHelper's cached brightness values or matrices.
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, previousLightX, previousLightY);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();
            GL11.glPopAttrib();
            OpenGlHelper.setActiveTexture(previousTextureUnit);
            GL11.glMatrixMode(previousMatrixMode);
        }
    }


    private static int brighten(int rgb, float factor) {
        int red = (int) (((rgb >> 16) & 255) * factor), green = (int) (((rgb >> 8) & 255) * factor), blue = (int) ((rgb & 255) * factor);
        return (red << 16) | (green << 8) | blue;
    }

    private void drawPanel(HologramLayout layout, int color, float fade) {
        int width = layout.width;
        int height = layout.height;
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        rectangle(0, 0, width, height, HologramPalette.panelBackground(color), 0.82F * fade);
        rectangle(0, 0, width, 1, color, 0.85F * fade);
        rectangle(0, height - 1, width, height, color, 0.55F * fade);
        rectangle(0, 1, 1, height - 1, color, 0.55F * fade);
        rectangle(width - 1, 1, width, height - 1, color, 0.55F * fade);
        for (int b = 0; b < layout.bandCount() - 1; b++) {
            rectangle(4, layout.separatorY(b), width - 4, layout.separatorY(b) + 1, color, 0.50F * fade);
        }
        for (int slot : layout.allSlots()) slot(layout.slotX(slot), layout.slotY(slot), color, fade);
    }

    private void slot(int x, int y, int color, float fade) {
        rectangle(x - 1, y - 1, x + 17, y + 17, color, 0.20F * fade);
        rectangle(x, y, x + 16, y + 16, HologramPalette.panelBackground(color), 0.75F * fade);
    }

    private static void rectangle(int left, int top, int right, int bottom, int color, float alpha) {
        GL11.glColor4f(((color >> 16) & 255) / 255.0F,
            ((color >> 8) & 255) / 255.0F, (color & 255) / 255.0F, alpha);
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.addVertex(left, bottom, 0.0D);
        tessellator.addVertex(right, bottom, 0.0D);
        tessellator.addVertex(right, top, 0.0D);
        tessellator.addVertex(left, top, 0.0D);
        tessellator.draw();
    }

    private void setTextureFade(float fade) {
        // RenderItem overwrites glColor's alpha. A texture-environment constant fades icons too.
        fadeColor.clear();
        fadeColor.put(1.0F).put(1.0F).put(1.0F).put(fade);
        fadeColor.flip();
        GL11.glTexEnv(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_COLOR, fadeColor);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL13.GL_COMBINE);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_COMBINE_RGB, GL11.GL_MODULATE);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE0_RGB, GL11.GL_TEXTURE);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE1_RGB, GL13.GL_PRIMARY_COLOR);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_OPERAND0_RGB, GL11.GL_SRC_COLOR);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_OPERAND1_RGB, GL11.GL_SRC_COLOR);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_COMBINE_ALPHA, GL11.GL_MODULATE);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE0_ALPHA, GL11.GL_TEXTURE);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE1_ALPHA, GL13.GL_CONSTANT);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_OPERAND0_ALPHA, GL11.GL_SRC_ALPHA);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_OPERAND1_ALPHA, GL11.GL_SRC_ALPHA);
    }

    private void drawItem(Minecraft mc, ItemStack stack, int x, int y) {
        if (stack == null || stack.getItem() == null) return;
        float previousZLevel = itemRenderer.zLevel;
        boolean previousTint = blockRenderer.useInventoryTint;
        float previousLightX = OpenGlHelper.lastBrightnessX;
        float previousLightY = OpenGlHelper.lastBrightnessY;
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        try {
            GL11.glEnable(GL12.GL_RESCALE_NORMAL);
            RenderHelper.enableGUIStandardItemLighting();
            itemRenderer.zLevel = 0.0F;
            // Custom renderers own their drawing state and may not retain the texture fade.
            if (!ForgeHooksClient.renderInventoryItem(blockRenderer, mc.getTextureManager(), stack,
                itemRenderer.renderWithColor, itemRenderer.zLevel, x, y)) {
                Block block = Block.getBlockFromItem(stack.getItem());
                if (stack.getItemSpriteNumber() == 0 && RenderBlocks.renderItemIn3d(block.getRenderType())
                    && block.getRenderBlockPass() == 0) {
                    drawOpaqueBlock(mc, stack, block, x, y);
                } else {
                    // No glint pass: it needs GUI depth writes, inappropriate for a hologram.
                    itemRenderer.renderItemIntoGUI(mc.fontRenderer, mc.getTextureManager(), stack, x, y);
                }
            }
        } finally {
            itemRenderer.zLevel = previousZLevel;
            blockRenderer.useInventoryTint = previousTint;
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, previousLightX, previousLightY);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    private void drawOpaqueBlock(Minecraft mc, ItemStack stack, Block block, int x, int y) {
        mc.getTextureManager().bindTexture(TextureMap.locationBlocksTexture);
        // RenderItem's opaque branch disables blending and clips the fade at alpha 0.5.
        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glAlphaFunc(GL11.GL_GREATER, 0.001F);
        GL11.glTranslatef(x - 2, y + 3, -3.0F + itemRenderer.zLevel);
        GL11.glScalef(10.0F, 10.0F, 10.0F);
        GL11.glTranslatef(1.0F, 0.5F, 1.0F);
        GL11.glScalef(1.0F, 1.0F, -1.0F);
        GL11.glRotatef(210.0F, 1.0F, 0.0F, 0.0F);
        GL11.glRotatef(45.0F, 0.0F, 1.0F, 0.0F);
        if (itemRenderer.renderWithColor) {
            int color = stack.getItem().getColorFromItemStack(stack, 0);
            GL11.glColor4f(((color >> 16) & 255) / 255.0F,
                ((color >> 8) & 255) / 255.0F, (color & 255) / 255.0F, 1.0F);
        }
        GL11.glRotatef(-90.0F, 0.0F, 1.0F, 0.0F);
        blockRenderer.useInventoryTint = itemRenderer.renderWithColor;
        blockRenderer.renderBlockAsItem(block, stack.getItemDamage(), 1.0F);
    }
}
