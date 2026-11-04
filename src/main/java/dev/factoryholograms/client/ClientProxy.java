package dev.factoryholograms.client;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.StatCollector;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import cpw.mods.fml.client.event.ConfigChangedEvent;
import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import dev.factoryholograms.CommonProxy;
import dev.factoryholograms.FactoryHolograms;
import dev.factoryholograms.HologramConfig;
import dev.factoryholograms.HologramLabel;
import dev.factoryholograms.HologramPlacement;
import dev.factoryholograms.LabelRules;
import dev.factoryholograms.LocalLabelStore;
import dev.factoryholograms.LocalWorldKey;

@SideOnly(Side.CLIENT)
public final class ClientProxy extends CommonProxy {
    private final KeyBinding toggleHolograms = new KeyBinding(
        "key.factoryholograms.toggle", Keyboard.KEY_H, "key.categories.factoryholograms");
    private final HologramRenderer renderer = new HologramRenderer();
    private World sessionWorld;
    private LocalLabelStore store;
    private boolean loadFailed;
    private List<HologramLabel> renderLabels = Collections.emptyList();
    private final RenderItem tooltipItemRenderer = new RenderItem();
    private HologramHover hover;
    private WorldRecipeHooks recipeHooks;
    private boolean recipeHooksLoaded;

    @Override public void init() {
        ClientRegistry.registerKeyBinding(toggleHolograms);
        FMLCommonHandler.instance().bus().register(this);
        MinecraftForge.EVENT_BUS.register(this);
        ClientCommandHandler.instance.registerCommand(new PathCommand());
    }

    private void clearSession() {
        sessionWorld = null;
        store = null;
        loadFailed = false;
        renderLabels = Collections.emptyList();
        hover = null;
    }

    /** NEI classes are only linked when NEI is present; null otherwise. */
    private WorldRecipeHooks recipeHooks() {
        if (!recipeHooksLoaded) {
            recipeHooksLoaded = true;
            if (Loader.isModLoaded("NotEnoughItems")) {
                try {
                    recipeHooks = Class.forName("dev.factoryholograms.client.nei.NeiWorldHooks")
                        .asSubclass(WorldRecipeHooks.class).getDeclaredConstructor().newInstance();
                } catch (Throwable failure) {
                    if (FactoryHolograms.log != null) FactoryHolograms.log.warn("NEI recipe hooks unavailable", failure);
                    recipeHooks = null;
                }
            }
        }
        return recipeHooks;
    }

    private boolean ensureSession(boolean retry) {
        Minecraft mc = Minecraft.getMinecraft();
        World world = mc.theWorld;
        if (world == null || !world.isRemote) { clearSession(); return false; }
        if (sessionWorld == world && store != null) return true;
        if (sessionWorld == world && loadFailed && !retry) return false;
        final String identity;
        boolean singleplayer = mc.isSingleplayer();
        if (singleplayer) {
            if (mc.getIntegratedServer() == null) return false;
            identity = mc.getIntegratedServer().getFolderName();
        } else {
            ServerData server = mc.func_147104_D();
            if (server == null || server.serverIP == null || server.serverIP.trim().isEmpty()) return false;
            identity = server.serverIP;
        }
        if (sessionWorld != world) {
            clearSession();
            if (mc.currentScreen instanceof HologramGui) mc.displayGuiScreen(null);
        }
        sessionWorld = world;
        File file = LocalWorldKey.dataFile(mc.mcDataDir, singleplayer, identity, world.provider.dimensionId);
        try {
            store = new LocalLabelStore(file, world);
            loadFailed = false;
            refreshRenderLabels();
            if (FactoryHolograms.log != null) FactoryHolograms.log.info("Local holograms: {}", file.getAbsolutePath());
            return true;
        } catch (IOException failure) {
            store = null;
            loadFailed = true;
            report("load", file, failure);
            return false;
        }
    }

    private void refreshRenderLabels() {
        // Snapshot once after loading/committing, not NBT-copying every icon on every frame.
        renderLabels = store == null ? Collections.<HologramLabel>emptyList() : new ArrayList<HologramLabel>(store.labels());
    }

    public boolean saveDraft(HologramLabel draft) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!ensureSession(false) || draft.getWorld() != sessionWorld || mc.thePlayer == null || mc.thePlayer.isDead) return false;
        if (!isChunkLoaded(sessionWorld, draft.x >> 4, draft.z >> 4) || !draft.matchesBlock()) {
            chat(StatCollector.translateToLocal("gui.factoryholograms.error.anchor"));
            return false;
        }
        try {
            store.put(draft);
            refreshRenderLabels();
            return true;
        } catch (IOException failure) {
            report("save", store.getFile(), failure);
            return false;
        }
    }

    public boolean deleteLabel(HologramLabel draft) {
        if (!ensureSession(false) || draft.getWorld() != sessionWorld) return false;
        try {
            store.remove(draft.x, draft.y, draft.z);
            refreshRenderLabels();
            return true;
        } catch (IOException failure) {
            report("save", store.getFile(), failure);
            return false;
        }
    }

    private final class PathCommand extends CommandBase {
        @Override public String getCommandName() { return "fholograms"; }
        @Override public String getCommandUsage(ICommandSender sender) { return "/fholograms path"; }
        @Override public int getRequiredPermissionLevel() { return 0; }
        @Override public void processCommand(ICommandSender sender, String[] arguments) {
            if (ensureSession(true)) chat(store.getFile().getAbsolutePath());
        }
    }

    private static void report(String action, File file, IOException failure) {
        if (FactoryHolograms.log != null) FactoryHolograms.log.error("Cannot " + action + " local holograms: " + file, failure);
        String key = "gui.factoryholograms.error.local." + action;
        String message = StatCollector.canTranslate(key) ? StatCollector.translateToLocal(key) : "Could not " + action + " local holograms. See the log.";
        chat(message + " " + file.getAbsolutePath());
    }

    private static void chat(String message) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer != null) mc.thePlayer.addChatMessage(new ChatComponentText("[Factory Holograms] " + message));
    }

    private static boolean isChunkLoaded(World world, int x, int z) {
        // ChunkProviderClient.chunkExists always returns true in vanilla 1.7.10.
        return world.getChunkProvider().chunkExists(x, z) && world.getChunkFromChunkCoords(x, z).isChunkLoaded;
    }

    @SubscribeEvent public void onWorldUnload(WorldEvent.Unload event) {
        if (event.world.isRemote && event.world == sessionWorld) clearSession();
    }

    @SubscribeEvent public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ensureSession(false);
    }

    @SubscribeEvent public void onRenderWorldLast(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        hover = null;
        if (mc.theWorld == null || mc.theWorld != sessionWorld || mc.gameSettings.hideGUI) return;
        HologramLabel preview = mc.currentScreen instanceof HologramGui ? ((HologramGui) mc.currentScreen).getPreviewLabel() : null;
        if (!HologramConfig.enabled && preview == null) return;
        Entity camera = mc.renderViewEntity != null ? mc.renderViewEntity : mc.thePlayer;
        if (camera == null || camera.worldObj != sessionWorld) return;
        double x = camera.lastTickPosX + (camera.posX - camera.lastTickPosX) * event.partialTicks;
        double y = camera.lastTickPosY + (camera.posY - camera.lastTickPosY) * event.partialTicks;
        double z = camera.lastTickPosZ + (camera.posZ - camera.lastTickPosZ) * event.partialTicks;
        double margin = HologramConfig.renderDistance + LabelRules.MAX_OFFSET * 2 + 3;
        hover = findHover(mc, event.partialTicks, x, y, z, margin);
        for (HologramLabel label : renderLabels) {
            if (!HologramConfig.enabled) break;
            if (HologramPlacement.sameAnchor(label, preview)) continue;
            double dx = label.x - x, dy = label.y - y, dz = label.z - z;
            if (dx * dx + dy * dy + dz * dz > margin * margin) continue;
            if (isChunkLoaded(sessionWorld, label.x >> 4, label.z >> 4) && label.matchesBlock()) {
                renderer.render(label, x, y, z, camera.getEyeHeight(), false,
                    hover != null && hover.label == label ? hover.slot : -1);
            }
        }
        if (preview != null && preview.getWorld() == sessionWorld
            && isChunkLoaded(sessionWorld, preview.x >> 4, preview.z >> 4) && preview.matchesBlock()) {
            renderer.render(preview, x, y, z, camera.getEyeHeight(), true);
        }
    }

    private HologramHover findHover(Minecraft mc, float partialTicks, double x, double y, double z, double margin) {
        EntityLivingBase view = mc.renderViewEntity != null ? mc.renderViewEntity : mc.thePlayer;
        if (!HologramConfig.enabled || !HologramConfig.hoverTooltips || mc.currentScreen != null
            || mc.gameSettings.thirdPersonView != 0 || mc.thePlayer == null || mc.thePlayer.isDead
            || view == null || view.worldObj != sessionWorld) return null;
        Vec3 origin = view.getPosition(partialTicks);
        Vec3 look = view.getLook(partialTicks);
        List<HologramHover> candidates = null;
        for (HologramLabel label : renderLabels) {
            double dx = label.x - x, dy = label.y - y, dz = label.z - z;
            if (dx * dx + dy * dy + dz * dz > margin * margin) continue;
            if (!isChunkLoaded(sessionWorld, label.x >> 4, label.z >> 4) || !label.matchesBlock()) continue;
            HologramHover candidate = renderer.pick(label, origin.xCoord, origin.yCoord, origin.zCoord,
                look.xCoord, look.yCoord, look.zCoord);
            if (candidate == null) continue;
            if (candidates == null) candidates = new ArrayList<HologramHover>();
            candidates.add(candidate);
        }
        if (candidates == null) return null;
        Collections.sort(candidates, new Comparator<HologramHover>() {
            @Override public int compare(HologramHover a, HologramHover b) { return Double.compare(a.distance, b.distance); }
        });
        for (HologramHover candidate : candidates) {
            if (!occluded(origin, look, candidate)) return candidate;
        }
        return null;
    }

    private boolean occluded(Vec3 origin, Vec3 look, HologramHover candidate) {
        Vec3 start = Vec3.createVectorHelper(origin.xCoord, origin.yCoord, origin.zCoord);
        Vec3 end = Vec3.createVectorHelper(origin.xCoord + look.xCoord * candidate.distance,
            origin.yCoord + look.yCoord * candidate.distance, origin.zCoord + look.zCoord * candidate.distance);
        // World.func_147447_a(start, end, stopOnLiquid, ignoreBlockWithoutBoundingBox, returnLastUncollidableBlock)
        MovingObjectPosition block = sessionWorld.func_147447_a(start, end, false, true, false);
        if (block == null || block.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return false;
        HologramLabel label = candidate.label;
        if (block.blockX == label.x && block.blockY == label.y && block.blockZ == label.z) return false;
        return block.hitVec.distanceTo(origin) < candidate.distance - 0.01D;
    }

    @SubscribeEvent public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        Minecraft mc = Minecraft.getMinecraft();
        HologramHover current = hover;
        if (event.type != RenderGameOverlayEvent.ElementType.ALL || current == null || mc.currentScreen != null
            || mc.thePlayer == null || !HologramConfig.hoverTooltips || event.resolution == null) return;
        ItemStack stack = current.stack;
        FontRenderer font = mc.fontRenderer;
        @SuppressWarnings("unchecked")
        List<String> tooltip = new ArrayList<String>(stack.getTooltip(mc.thePlayer, mc.gameSettings.advancedItemTooltips));
        if (tooltip.isEmpty()) return;
        for (int i = 0; i < tooltip.size(); i++) {
            tooltip.set(i, (i == 0 ? stack.getRarity().rarityColor : EnumChatFormatting.GRAY) + tooltip.get(i));
        }
        boolean shift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
        if (!shift) {
            String first = tooltip.get(0);
            tooltip.clear();
            tooltip.add(first);
            tooltip.add(EnumChatFormatting.DARK_GRAY + hint());
        }
        int textWidth = 0;
        for (String line : tooltip) textWidth = Math.max(textWidth, font.getStringWidth(line));
        int count = tooltip.size();
        int contentWidth = 20 + textWidth;
        int contentHeight = 16 + (count - 1) * 10;
        int screenWidth = event.resolution.getScaledWidth();
        int screenHeight = event.resolution.getScaledHeight();
        int left = screenWidth / 2 + 10;
        int top = screenHeight / 2 - contentHeight / 2;
        left = Math.max(4, Math.min(left, screenWidth - contentWidth - 4));
        top = Math.max(4, Math.min(top, screenHeight - contentHeight - 4));

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();
        try {
            GL11.glDisable(GL12.GL_RESCALE_NORMAL);
            RenderHelper.disableStandardItemLighting();
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            int background = 0xF0100010;
            int borderTop = 0x505000FF;
            int borderBottom = (borderTop & 0xFEFEFE) >> 1 | borderTop & 0xFF000000;
            int right = left + contentWidth;
            int bottom = top + contentHeight;
            gradient(left - 3, top - 4, right + 3, top - 3, background, background);
            gradient(left - 3, bottom + 3, right + 3, bottom + 4, background, background);
            gradient(left - 3, top - 3, right + 3, bottom + 3, background, background);
            gradient(left - 4, top - 3, left - 3, bottom + 3, background, background);
            gradient(right + 3, top - 3, right + 4, bottom + 3, background, background);
            gradient(left - 3, top - 2, left - 2, bottom + 2, borderTop, borderBottom);
            gradient(right + 2, top - 2, right + 3, bottom + 2, borderTop, borderBottom);
            gradient(left - 3, top - 3, right + 3, top - 2, borderTop, borderTop);
            gradient(left - 3, bottom + 2, right + 3, bottom + 3, borderBottom, borderBottom);

            GL11.glEnable(GL12.GL_RESCALE_NORMAL);
            RenderHelper.enableGUIStandardItemLighting();
            tooltipItemRenderer.zLevel = 300.0F;
            tooltipItemRenderer.renderItemAndEffectIntoGUI(font, mc.getTextureManager(), stack, left, top);
            tooltipItemRenderer.zLevel = 0.0F;
            RenderHelper.disableStandardItemLighting();
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL12.GL_RESCALE_NORMAL);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            for (int i = 0; i < count; i++) {
                font.drawStringWithShadow(tooltip.get(i), left + 20, i == 0 ? top + 4 : top + 18 + (i - 1) * 10, -1);
            }
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        }
    }

    private String hint() {
        StringBuilder text = new StringBuilder(StatCollector.translateToLocal("gui.factoryholograms.hover.shift"));
        WorldRecipeHooks hooks = recipeHooks();
        if (hooks != null) {
            if (hooks.recipeKey() > 0) {
                text.append("  ").append(StatCollector.translateToLocalFormatted(
                    "gui.factoryholograms.hover.recipe", Keyboard.getKeyName(hooks.recipeKey())));
            }
            if (hooks.usageKey() > 0) {
                text.append("  ").append(StatCollector.translateToLocalFormatted(
                    "gui.factoryholograms.hover.usage", Keyboard.getKeyName(hooks.usageKey())));
            }
        }
        return text.toString();
    }

    private static void gradient(int left, int top, int right, int bottom, int startColor, int endColor) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glShadeModel(GL11.GL_SMOOTH);
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.setColorRGBA_F((startColor >> 16 & 255) / 255.0F, (startColor >> 8 & 255) / 255.0F,
            (startColor & 255) / 255.0F, (startColor >> 24 & 255) / 255.0F);
        tessellator.addVertex(right, top, 300.0D);
        tessellator.addVertex(left, top, 300.0D);
        tessellator.setColorRGBA_F((endColor >> 16 & 255) / 255.0F, (endColor >> 8 & 255) / 255.0F,
            (endColor & 255) / 255.0F, (endColor >> 24 & 255) / 255.0F);
        tessellator.addVertex(left, bottom, 300.0D);
        tessellator.addVertex(right, bottom, 300.0D);
        tessellator.draw();
        GL11.glShadeModel(GL11.GL_FLAT);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    @SubscribeEvent public void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        if (FactoryHolograms.ID.equals(event.modID)) HologramConfig.sync();
    }

    @SubscribeEvent public void onKeyInput(InputEvent.KeyInputEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        HologramHover current = hover;
        WorldRecipeHooks hooks = current == null || mc.currentScreen != null || !Keyboard.getEventKeyState() ? null : recipeHooks();
        if (hooks != null) {
            int key = Keyboard.getEventKey();
            try {
                if (key != Keyboard.KEY_NONE && key == hooks.recipeKey()) hooks.showRecipe(current.stack);
                else if (key != Keyboard.KEY_NONE && key == hooks.usageKey()) hooks.showUsage(current.stack);
            } catch (Throwable failure) {
                if (FactoryHolograms.log != null) FactoryHolograms.log.warn("NEI recipe lookup failed", failure);
            }
        }
        while (toggleHolograms.isPressed()) {
            if (mc.currentScreen != null || mc.theWorld == null || mc.thePlayer == null || mc.thePlayer.isDead) continue;
            if (!Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) && !Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)) {
                HologramConfig.setEnabled(!HologramConfig.enabled);
                continue;
            }
            MovingObjectPosition hit = mc.objectMouseOver;
            if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK
                || mc.thePlayer.getDistanceSq(hit.blockX + 0.5, hit.blockY + 0.5, hit.blockZ + 0.5) > 36.0
                || !isChunkLoaded(mc.theWorld, hit.blockX >> 4, hit.blockZ >> 4)
                || mc.theWorld.isAirBlock(hit.blockX, hit.blockY, hit.blockZ) || !ensureSession(true)) continue;
            int face = HologramPlacement.horizontalFace(hit.sideHit,
                mc.thePlayer.posX - hit.blockX - 0.5, mc.thePlayer.posZ - hit.blockZ - 0.5);
            HologramLabel draft = store.draftForEditing(hit.blockX, hit.blockY, hit.blockZ, face);
            mc.displayGuiScreen(new HologramGui(draft, this));
        }
    }
}
