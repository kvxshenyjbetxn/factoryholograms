package dev.factoryholograms.client;

import java.awt.image.BufferedImage;
import java.io.InputStream;

import javax.imageio.ImageIO;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.util.ResourceLocation;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Picks label colors based on the brightness of the (possibly resource-pack themed) panel texture. */
@SideOnly(Side.CLIENT)
final class GuiTheme {
    private static final ResourceLocation PANEL = new ResourceLocation("textures/gui/demo_background.png");
    private static Boolean dark;
    private static boolean listenerRegistered;

    private GuiTheme() {}

    static int label() {
        return isDark() ? 0xE0E0E0 : 0x404040;
    }

    static int hint() {
        return isDark() ? 0xA0A0A0 : 0x606060;
    }

    private static synchronized boolean isDark() {
        registerListener();
        if (dark == null) {
            dark = Boolean.valueOf(sample());
        }
        return dark.booleanValue();
    }

    private static void registerListener() {
        if (listenerRegistered) return;
        try {
            IResourceManager rm = Minecraft.getMinecraft().getResourceManager();
            if (rm instanceof IReloadableResourceManager) {
                ((IReloadableResourceManager) rm).registerReloadListener(new IResourceManagerReloadListener() {
                    @Override
                    public void onResourceManagerReload(IResourceManager manager) {
                        synchronized (GuiTheme.class) {
                            dark = null;
                        }
                    }
                });
                listenerRegistered = true;
            }
        } catch (Throwable ignored) {
        }
    }

    private static boolean sample() {
        InputStream in = null;
        try {
            in = Minecraft.getMinecraft().getResourceManager().getResource(PANEL).getInputStream();
            BufferedImage img = ImageIO.read(in);
            if (img == null) return false;
            double scale = img.getWidth() / 256.0;
            int x0 = (int) (8 * scale), x1 = Math.min((int) (239 * scale), img.getWidth() - 1);
            int y0 = (int) (8 * scale), y1 = Math.min((int) (157 * scale), img.getHeight() - 1);
            int step = Math.max(1, (int) Math.round(4 * scale));
            double sum = 0;
            long n = 0;
            for (int y = y0; y <= y1; y += step) {
                for (int x = x0; x <= x1; x += step) {
                    int p = img.getRGB(x, y);
                    sum += 0.299 * ((p >> 16) & 0xFF) + 0.587 * ((p >> 8) & 0xFF) + 0.114 * (p & 0xFF);
                    n++;
                }
            }
            return n > 0 && sum / n < 128;
        } catch (Throwable t) {
            return false;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }
}
