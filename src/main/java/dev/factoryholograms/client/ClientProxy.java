package dev.factoryholograms.client;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraft.entity.Entity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;
import org.lwjgl.input.Keyboard;

import cpw.mods.fml.client.event.ConfigChangedEvent;
import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
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
        if (mc.theWorld == null || mc.theWorld != sessionWorld || mc.gameSettings.hideGUI) return;
        HologramLabel preview = mc.currentScreen instanceof HologramGui ? ((HologramGui) mc.currentScreen).getPreviewLabel() : null;
        if (!HologramConfig.enabled && preview == null) return;
        Entity camera = mc.renderViewEntity != null ? mc.renderViewEntity : mc.thePlayer;
        if (camera == null || camera.worldObj != sessionWorld) return;
        double x = camera.lastTickPosX + (camera.posX - camera.lastTickPosX) * event.partialTicks;
        double y = camera.lastTickPosY + (camera.posY - camera.lastTickPosY) * event.partialTicks;
        double z = camera.lastTickPosZ + (camera.posZ - camera.lastTickPosZ) * event.partialTicks;
        double margin = HologramConfig.renderDistance + LabelRules.MAX_OFFSET * 2 + 3;
        for (HologramLabel label : renderLabels) {
            if (!HologramConfig.enabled) break;
            if (HologramPlacement.sameAnchor(label, preview)) continue;
            double dx = label.x - x, dy = label.y - y, dz = label.z - z;
            if (dx * dx + dy * dy + dz * dz > margin * margin) continue;
            if (isChunkLoaded(sessionWorld, label.x >> 4, label.z >> 4) && label.matchesBlock()) {
                renderer.render(label, x, y, z, camera.getEyeHeight());
            }
        }
        if (preview != null && preview.getWorld() == sessionWorld
            && isChunkLoaded(sessionWorld, preview.x >> 4, preview.z >> 4) && preview.matchesBlock()) {
            renderer.render(preview, x, y, z, camera.getEyeHeight(), true);
        }
    }

    @SubscribeEvent public void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        if (FactoryHolograms.ID.equals(event.modID)) HologramConfig.sync();
    }

    @SubscribeEvent public void onKeyInput(InputEvent.KeyInputEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
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
