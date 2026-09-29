package com.caleon.client.module;

import com.caleon.client.render.RenderUtil;
import net.minecraft.block.entity.*;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.StorageMinecartEntity;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class BaseModules {
    public static SusChunkFinder susFinder;
    public static HoleEsp holeEsp;
    public static TunnelBaseFinder tunnelFinder;
    public static BlockEsp blockEsp;
    public static NetheriteFinder netheriteFinder;
    public static LightFinder lightFinder;
    public static SuspiciousEsp suspiciousEsp;
    public static PortalEsp portalEsp;

    static boolean near(BlockPos p, Vec3d cam, double range) {
        double dx = p.getX() + 0.5 - cam.x, dy = p.getY() + 0.5 - cam.y, dz = p.getZ() + 0.5 - cam.z;
        return dx * dx + dy * dy + dz * dz <= range * range;
    }

    static void cube(Matrix4f m, Vec3d cam, BlockPos p, int rgb, boolean tracer) {
        RenderUtil.esp(m, cam, p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 1, p.getZ() + 1, rgb, tracer);
    }

    static boolean isStorage(BlockEntity be) {
        return be instanceof ChestBlockEntity || be instanceof EnderChestBlockEntity || be instanceof BarrelBlockEntity
                || be instanceof ShulkerBoxBlockEntity || be instanceof HopperBlockEntity
                || be instanceof DispenserBlockEntity || be instanceof AbstractFurnaceBlockEntity;
    }

    // ================= block-entity based ESPs (live, no scanning delay) =================
    abstract static class BeEsp extends Module {
        final Setting.Num range = add(new Setting.Num("Range", 128, 32, 256));
        final Setting.Bool tracers;
        BeEsp(String name, boolean tracerDefault) {
            super(name, Category.BASEFINDING);
            tracers = add(new Setting.Bool("Tracers", tracerDefault));
        }
        /** RGB colour, or 0 to skip this block entity. */
        abstract int color(BlockEntity be);
        @Override public void onRender(Matrix4f m, Vec3d cam) {
            for (BlockEntity be : BaseScanner.bes()) {
                int c = color(be);
                if (c == 0) continue;
                BlockPos p = be.getPos();
                if (!near(p, cam, range.value)) continue;
                boolean chest = be instanceof ChestBlockEntity || be instanceof EnderChestBlockEntity;
                double in = chest ? 0.06 : 0.02, top = chest ? 0.875 : 1.0;
                RenderUtil.esp(m, cam, p.getX() + in, p.getY(), p.getZ() + in, p.getX() + 1 - in, p.getY() + top, p.getZ() + 1 - in, c, tracers.value);
            }
        }
    }

    public static class StorageEsp extends BeEsp {
        final Setting.Bool chests = add(new Setting.Bool("Chests", true));
        final Setting.Bool trapped = add(new Setting.Bool("Trapped chests", true));
        final Setting.Bool ender = add(new Setting.Bool("Ender chests", true));
        final Setting.Bool barrels = add(new Setting.Bool("Barrels", true));
        final Setting.Bool shulkers = add(new Setting.Bool("Shulkers", true));
        final Setting.Bool hoppers = add(new Setting.Bool("Hoppers", true));
        final Setting.Bool furnaces = add(new Setting.Bool("Furnaces", true));
        final Setting.Bool dispensers = add(new Setting.Bool("Dispensers/Droppers", false));
        final Setting.Bool frames = add(new Setting.Bool("Item frames", true));
        final Setting.Bool minecarts = add(new Setting.Bool("Chest minecarts", true));
        public StorageEsp() { super("Storage ESP", true); }
        @Override int color(BlockEntity be) {
            if (be instanceof TrappedChestBlockEntity) return trapped.value ? 0xFF3333 : 0;
            if (be instanceof ChestBlockEntity) return chests.value ? 0xFFAA33 : 0;
            if (be instanceof EnderChestBlockEntity) return ender.value ? 0xAA33FF : 0;
            if (be instanceof BarrelBlockEntity) return barrels.value ? 0xBB8855 : 0;
            if (be instanceof ShulkerBoxBlockEntity) return shulkers.value ? 0xFF77CC : 0;
            if (be instanceof HopperBlockEntity) return hoppers.value ? 0xDDDDDD : 0;
            if (be instanceof AbstractFurnaceBlockEntity) return furnaces.value ? 0xFF8800 : 0;
            if (be instanceof DispenserBlockEntity) return dispensers.value ? 0x8899AA : 0;
            return 0;
        }
        @Override public void onRender(Matrix4f m, Vec3d cam) {
            super.onRender(m, cam);
            if (!frames.value && !minecarts.value) return;
            for (Entity e : mc.world.getEntities()) {
                if (!EntityModules.inRange(e, cam, range.value)) continue;
                if (frames.value && e instanceof net.minecraft.entity.decoration.ItemFrameEntity)
                    EntityModules.draw(m, cam, e, 0x33DDFF, tracers.value);
                else if (minecarts.value && e instanceof StorageMinecartEntity)
                    EntityModules.draw(m, cam, e, 0xFFAA33, tracers.value);
            }
        }
    }

    public static class HopperEsp extends BeEsp {
        public HopperEsp() { super("Hopper ESP", true); }
        @Override int color(BlockEntity be) { return be instanceof HopperBlockEntity ? 0xEEEEEE : 0; }
    }

    public static class FurnaceEsp extends BeEsp {
        final Setting.Bool litOnly = add(new Setting.Bool("Lit only", false));
        public FurnaceEsp() { super("Furnace ESP", false); }
        @Override int color(BlockEntity be) {
            if (!(be instanceof AbstractFurnaceBlockEntity)) return 0;
            var st = be.getCachedState();
            boolean lit = st.contains(Properties.LIT) && st.get(Properties.LIT);
            if (litOnly.value && !lit) return 0;
            return lit ? 0xFFDD00 : 0xFF7700;
        }
    }

    public static class BedEsp extends BeEsp {
        public BedEsp() { super("Bed ESP", false); }
        @Override int color(BlockEntity be) { return be instanceof BedBlockEntity ? 0xFF4466 : 0; }
    }

    public static class SignEsp extends BeEsp {
        public SignEsp() { super("Sign ESP", false); }
        @Override int color(BlockEntity be) { return be instanceof SignBlockEntity ? 0xE8D9A0 : 0; }
    }

    public static class UtilityEsp extends BeEsp {
        public UtilityEsp() { super("Utility ESP", false); }
        @Override int color(BlockEntity be) {
            if (be instanceof BeaconBlockEntity) return 0x55FFFF;
            if (be instanceof EnchantingTableBlockEntity || be instanceof BrewingStandBlockEntity
                    || be instanceof LecternBlockEntity || be instanceof CrafterBlockEntity) return 0x8899FF;
            return 0;
        }
    }

    // ================= chunk-based hunters =================
    abstract static class ChunkHunter extends Module {
        final Setting.Num threshold, range;
        final Setting.Bool chat, tracers;
        final String label; final int color;
        Map<Long, double[]> found = new HashMap<>();
        final Set<Long> reported = new HashSet<>();
        ClientWorld lastWorld; int t;

        ChunkHunter(String name, String label, int color, double def, double max) {
            super(name, Category.BASEFINDING);
            this.label = label; this.color = color;
            threshold = add(new Setting.Num("Threshold", def, 2, max));
            range = add(new Setting.Num("Range", 192, 32, 320));
            chat = add(new Setting.Bool("Chat alert", true));
            tracers = add(new Setting.Bool("Tracers", true));
        }

        abstract void collect(Map<Long, double[]> out);

        static void tally(Map<Long, double[]> out, long key, double y) {
            double[] a = out.computeIfAbsent(key, k -> new double[2]);
            a[0]++; a[1] += y;
        }

        @Override public void onTick() {
            if (mc.world != lastWorld) { lastWorld = mc.world; found = new HashMap<>(); reported.clear(); }
            if (++t % 20 != 0) return;
            Map<Long, double[]> out = new HashMap<>();
            collect(out);
            found = out;
            for (Map.Entry<Long, double[]> e : out.entrySet()) {
                if (e.getValue()[0] < threshold.value || !reported.add(e.getKey())) continue;
                BaseScanner.finds++;
                if (chat.value) {
                    ChunkPos cp = new ChunkPos(e.getKey());
                    mc.player.sendMessage(Text.literal("§c[Caleon] §f" + label + " at X " + cp.getCenterX() + " Z " + cp.getCenterZ()
                            + " (" + (int) e.getValue()[0] + ")"), false);
                }
            }
        }

        @Override public void onRender(Matrix4f m, Vec3d cam) {
            for (Map.Entry<Long, double[]> e : found.entrySet()) {
                double n = e.getValue()[0];
                if (n < threshold.value) continue;
                ChunkPos cp = new ChunkPos(e.getKey());
                double dx = cp.getCenterX() - cam.x, dz = cp.getCenterZ() - cam.z;
                if (dx * dx + dz * dz > range.value * range.value) continue;
                double y = e.getValue()[1] / n;
                RenderUtil.esp(m, cam, cp.getStartX(), y - 1, cp.getStartZ(), cp.getStartX() + 16, y + 3, cp.getStartZ() + 16, color, tracers.value);
            }
        }
    }

    /** Chunks holding lots of chests/barrels/shulkers/hoppers/furnaces. */
    public static class StashFinder extends ChunkHunter {
        public StashFinder() { super("Stash Finder", "Stash", 0xFF2244, 8, 60); }
        @Override void collect(Map<Long, double[]> out) {
            for (BlockEntity be : BaseScanner.bes())
                if (isStorage(be)) tally(out, ChunkPos.toLong(be.getPos()), be.getPos().getY());
        }
    }

    /** Chunks packed with mobs (mob farms / animal pens usually sit next to bases). */
    public static class MobFarmFinder extends ChunkHunter {
        public MobFarmFinder() { super("Mob Farm Finder", "Mob farm", 0xFF9922, 25, 120); }
        @Override void collect(Map<Long, double[]> out) {
            for (Entity e : mc.world.getEntities())
                if (e instanceof LivingEntity && !(e instanceof PlayerEntity) && !(e instanceof ArmorStandEntity))
                    tally(out, ChunkPos.toLong(e.getBlockPos()), e.getY());
        }
    }

    // ================= scanner based =================
    public static class HoleEsp extends Module {
        final Setting.Num minDepth = add(new Setting.Num("Min depth", 4, 2, 12));
        final Setting.Num range = add(new Setting.Num("Range", 128, 32, 256));
        final Setting.Bool tracers = add(new Setting.Bool("Tracers", false));
        public HoleEsp() { super("Hole ESP", Category.BASEFINDING); holeEsp = this; }
        @Override public void onRender(Matrix4f m, Vec3d cam) {
            for (BaseScanner.Data d : BaseScanner.DATA.values())
                for (BaseScanner.Hole h : d.holes) {
                    if (h.len() < minDepth.value || !near(h.pos(), cam, range.value)) continue;
                    BlockPos p = h.pos();
                    RenderUtil.esp(m, cam, p.getX() + 0.1, p.getY(), p.getZ() + 0.1, p.getX() + 0.9, p.getY() + h.len(), p.getZ() + 0.9, 0x22DD55, tracers.value);
                }
        }
    }

    public static class LightFinder extends Module {
        final Setting.Num maxY = add(new Setting.Num("Max Y", 64, -64, 320));
        final Setting.Num range = add(new Setting.Num("Range", 128, 32, 256));
        final Setting.Bool tracers = add(new Setting.Bool("Tracers", false));
        public LightFinder() { super("Light Finder", Category.BASEFINDING); lightFinder = this; }
        @Override public void onRender(Matrix4f m, Vec3d cam) {
            for (BaseScanner.Data d : BaseScanner.DATA.values())
                for (BlockPos p : d.lights) {
                    if (p.getY() > maxY.value || !near(p, cam, range.value)) continue;
                    cube(m, cam, p, 0xFFDD33, tracers.value);
                }
        }
    }

    public static class SuspiciousEsp extends Module {
        final Setting.Num range = add(new Setting.Num("Range", 128, 32, 256));
        final Setting.Bool tracers = add(new Setting.Bool("Tracers", false));
        public SuspiciousEsp() { super("Suspicious ESP", Category.BASEFINDING); suspiciousEsp = this; }
        @Override public void onRender(Matrix4f m, Vec3d cam) {
            for (BaseScanner.Data d : BaseScanner.DATA.values())
                for (BlockPos p : d.sus) {
                    if (!near(p, cam, range.value)) continue;
                    cube(m, cam, p, 0xFF4444, tracers.value);
                }
        }
    }

    public static class SusChunkFinder extends Module {
        public final Setting.Num threshold = add(new Setting.Num("Threshold", 12, 3, 60));
        public final Setting.Bool chatAlert = add(new Setting.Bool("Chat alert", true));
        final Setting.Bool tracers = add(new Setting.Bool("Tracers", true));
        final Setting.Num range = add(new Setting.Num("Range", 160, 32, 256));
        public SusChunkFinder() { super("SUS Chunk Finder", Category.BASEFINDING); susFinder = this; }
        @Override public void onRender(Matrix4f m, Vec3d cam) {
            double y = Math.floor(mc.player.getY());
            for (Map.Entry<Long, BaseScanner.Data> e : BaseScanner.DATA.entrySet()) {
                if (e.getValue().score < threshold.value) continue;
                ChunkPos cp = new ChunkPos(e.getKey());
                double cx = cp.getCenterX() - cam.x, cz = cp.getCenterZ() - cam.z;
                if (cx * cx + cz * cz > range.value * range.value) continue;
                RenderUtil.esp(m, cam, cp.getStartX(), y, cp.getStartZ(), cp.getStartX() + 16, y + 0.1, cp.getStartZ() + 16, 0xFF9922, tracers.value);
            }
        }
    }

    public static class PortalEsp extends Module {
        final Setting.Num range = add(new Setting.Num("Range", 160, 32, 256));
        final Setting.Bool tracers = add(new Setting.Bool("Tracers", true));
        public PortalEsp() { super("Portal ESP", Category.BASEFINDING); portalEsp = this; }
        @Override public void onRender(Matrix4f m, Vec3d cam) {
            for (BaseScanner.Data d : BaseScanner.DATA.values())
                for (BlockPos p : d.portals) {
                    if (!near(p, cam, range.value)) continue;
                    cube(m, cam, p, 0xAA44FF, tracers.value);
                }
        }
    }

    public static class BlockEsp extends Module {
        final Setting.Bool[] toggles = new Setting.Bool[BaseScanner.Ore.values().length];
        final Setting.Num range = add(new Setting.Num("Range", 128, 32, 256));
        final Setting.Bool tracers = add(new Setting.Bool("Tracers", false));
        public BlockEsp() {
            super("Block ESP", Category.BASEFINDING);
            for (BaseScanner.Ore o : BaseScanner.Ore.values())
                toggles[o.ordinal()] = add(new Setting.Bool(o.label, o == BaseScanner.Ore.DIAMOND || o == BaseScanner.Ore.DEBRIS || o == BaseScanner.Ore.SPAWNER));
            blockEsp = this;
        }
        @Override public void onRender(Matrix4f m, Vec3d cam) {
            for (BaseScanner.Data d : BaseScanner.DATA.values())
                for (BaseScanner.Find f : d.ores) {
                    if (!toggles[f.ore().ordinal()].value || !near(f.pos(), cam, range.value)) continue;
                    cube(m, cam, f.pos(), f.ore().color, tracers.value);
                }
        }
    }

    public static class NetheriteFinder extends Module {
        final Setting.Num range = add(new Setting.Num("Range", 192, 32, 256));
        final Setting.Bool tracers = add(new Setting.Bool("Tracers", true));
        public NetheriteFinder() { super("Netherite Finder", Category.BASEFINDING); netheriteFinder = this; }
        @Override public void onRender(Matrix4f m, Vec3d cam) {
            for (BaseScanner.Data d : BaseScanner.DATA.values())
                for (BaseScanner.Find f : d.ores) {
                    if (f.ore() != BaseScanner.Ore.DEBRIS || !near(f.pos(), cam, range.value)) continue;
                    cube(m, cam, f.pos(), 0xFF8833, tracers.value);
                }
        }
    }

    public static class TunnelBaseFinder extends Module {
        final Setting.Num minLen = add(new Setting.Num("Min length", 10, 6, 60));
        final Setting.Num range = add(new Setting.Num("Range", 160, 32, 256));
        final Setting.Bool tracers = add(new Setting.Bool("Tracers", false));
        public TunnelBaseFinder() { super("Tunnel Base Finder", Category.BASEFINDING); tunnelFinder = this; }
        @Override public void onRender(Matrix4f m, Vec3d cam) {
            for (BaseScanner.Data d : BaseScanner.DATA.values())
                for (BaseScanner.Tunnel t : d.tunnels) {
                    BlockPos p = t.start();
                    if (t.len() < minLen.value || !near(p, cam, range.value)) continue;
                    if (t.alongX())
                        RenderUtil.esp(m, cam, p.getX(), p.getY(), p.getZ() + 0.25, p.getX() + t.len(), p.getY() + 2, p.getZ() + 0.75, 0x4488FF, tracers.value);
                    else
                        RenderUtil.esp(m, cam, p.getX() + 0.25, p.getY(), p.getZ(), p.getX() + 0.75, p.getY() + 2, p.getZ() + t.len(), 0x4488FF, tracers.value);
                }
        }
    }
}
