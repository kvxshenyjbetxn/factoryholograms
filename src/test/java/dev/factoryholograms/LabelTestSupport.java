package dev.factoryholograms;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.profiler.Profiler;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraft.world.WorldProviderSurface;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldSettings.GameType;
import net.minecraft.world.WorldType;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.storage.SaveHandlerMP;

/** No chunks, save directory, running server or network connection are required. */
final class LabelTestSupport {
    static final class TestWorld extends World {
        final Map<String, Block> blocks = new HashMap<String, Block>();
        final Map<String, Integer> metadata = new HashMap<String, Integer>();
        boolean loaded = true;
        boolean mine = true;
        boolean visible = true;
        boolean traceRealBlocks;
        int hitX, hitY = 64, hitZ;
        int rayCalls;
        boolean mutateFirstRay;
        boolean fallbackOriginPreserved;
        private Vec3 originalRayStart;

        TestWorld() {
            super(new SaveHandlerMP(), "label-test", new WorldProviderSurface(),
                new WorldSettings(0, GameType.SURVIVAL, false, false, WorldType.DEFAULT), new Profiler());
        }
        private String key(int x, int y, int z) { return x + ":" + y + ":" + z; }
        void block(int x, int y, int z, Block block, int meta) {
            blocks.put(key(x, y, z), block);
            metadata.put(key(x, y, z), meta);
        }
        @Override public Block getBlock(int x, int y, int z) {
            Block block = blocks.get(key(x, y, z));
            return block == null ? (traceRealBlocks ? Blocks.air : Blocks.stone) : block;
        }
        @Override public int getBlockMetadata(int x, int y, int z) {
            Integer meta = metadata.get(key(x, y, z));
            return meta == null ? 0 : meta;
        }
        @Override public boolean isAirBlock(int x, int y, int z) { return getBlock(x, y, z) == Blocks.air; }
        @Override public boolean blockExists(int x, int y, int z) { return loaded && y >= 0 && y < 256; }
        @Override public boolean canMineBlock(EntityPlayer player, int x, int y, int z) { return mine; }
        @Override public MovingObjectPosition rayTraceBlocks(Vec3 from, Vec3 to, boolean liquids) {
            rayCalls++;
            if (traceRealBlocks) return super.rayTraceBlocks(from, to, liquids);
            if (mutateFirstRay && rayCalls == 1) {
                originalRayStart = Vec3.createVectorHelper(from.xCoord, from.yCoord, from.zCoord);
                from.xCoord += 100;
                from.yCoord += 100;
                from.zCoord += 100;
                return null;
            }
            if (mutateFirstRay && rayCalls == 2) fallbackOriginPreserved = from.xCoord == originalRayStart.xCoord
                && from.yCoord == originalRayStart.yCoord && from.zCoord == originalRayStart.zCoord;
            return visible ? new MovingObjectPosition(hitX, hitY, hitZ, 1, to) : null;
        }
        @Override public ChunkCoordinates getSpawnPoint() { return new ChunkCoordinates(0, 64, 0); }
        @Override protected IChunkProvider createChunkProvider() { throw new AssertionError("Unexpected chunk access"); }
        @Override protected int func_152379_p() { return 0; }
        @Override public Entity getEntityByID(int id) { return null; }
    }

    static final class TestPlayer extends EntityPlayer {
        boolean edit = true;
        TestPlayer(World world) {
            super(world, new GameProfile(UUID.fromString("00000000-0000-0000-0000-000000000001"), "LabelTest"));
            setPosition(0.5, 64.5, 2.5);
        }
        @Override public boolean canPlayerEdit(int x, int y, int z, int side, ItemStack held) { return edit; }
        @Override public void addChatMessage(IChatComponent message) { throw new AssertionError("Unexpected chat"); }
        @Override public boolean canCommandSenderUseCommand(int level, String command) { return false; }
        @Override public ChunkCoordinates getPlayerCoordinates() { return new ChunkCoordinates(0, 64, 0); }
    }
}
