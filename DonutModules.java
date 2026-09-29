package com.caleon.client.module;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.block.entity.TrialSpawnerBlockEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** DonutSMP-flavoured helpers: spawners, RTP hunting loop, AFK, player alerts. */
public class DonutModules {
    public static SpawnerFinder spawnerFinder;

    public static class SpawnerFinder extends Module {
        public int count;
        final Setting.Num range = add(new Setting.Num("Range", 256, 32, 512));
        final Setting.Num maxY = add(new Setting.Num("Max Y", 320, -64, 320));
        final Setting.Bool tracers = add(new Setting.Bool("Tracers", true));
        final Setting.Bool chat = add(new Setting.Bool("Chat alert", true));
        final Setting.Bool trial = add(new Setting.Bool("Trial spawners", false));
        private final Set<Long> alerted = new HashSet<>();
        private ClientWorld lastWorld;

        public SpawnerFinder() { super("Spawner Finder", Category.DONUT); spawnerFinder = this; }

        private boolean spawner(BlockEntity be) {
            return be instanceof MobSpawnerBlockEntity || (trial.value && be instanceof TrialSpawnerBlockEntity);
        }

        @Override public void onTick() {
            if (mc.world != lastWorld) { lastWorld = mc.world; alerted.clear(); }
            int n = 0;
            for (BlockEntity be : BaseScanner.bes()) {
                if (!spawner(be) || be.getPos().getY() > maxY.value) continue;
                n++;
                BlockPos p = be.getPos();
                if (alerted.add(p.asLong())) {
                    BaseScanner.finds++;
                    if (chat.value)
                        mc.player.sendMessage(Text.literal("§c[Caleon] §fSpawner at X " + p.getX() + " Y " + p.getY() + " Z " + p.getZ()), false);
                }
            }
            count = n;
        }

        @Override public void onDisable() { count = 0; }

        @Override public void onRender(Matrix4f m, Vec3d cam) {
            for (BlockEntity be : BaseScanner.bes()) {
                if (!spawner(be)) continue;
                BlockPos p = be.getPos();
                if (p.getY() > maxY.value || !BaseModules.near(p, cam, range.value)) continue;
                BaseModules.cube(m, cam, p, be instanceof MobSpawnerBlockEntity ? 0xFF33DD : 0x33FFDD, tracers.value);
            }
        }
    }

    /** Sends /rtp on a timer; by default stops itself as soon as any finder reports something. */
    public static class AutoRtp extends Module {
        final Setting.Num delay = add(new Setting.Num("Delay (s)", 30, 5, 300));
        final Setting.Bool stopOnFind = add(new Setting.Bool("Stop on find", true));
        private int timer, lastFinds;
        public AutoRtp() { super("Auto RTP", Category.DONUT); }
        @Override public void onEnable() { timer = 0; lastFinds = BaseScanner.finds; }
        @Override public void onTick() {
            if (stopOnFind.value && BaseScanner.finds != lastFinds) {
                mc.player.sendMessage(Text.literal("§c[Caleon] §fAuto RTP stopped: something was found."), false);
                toggle();
                return;
            }
            if (++timer >= delay.value * 20) {
                timer = 0;
                if (mc.getNetworkHandler() != null) mc.getNetworkHandler().sendChatCommand("rtp");
            }
        }
    }

    public static class AntiAfk extends Module {
        final Setting.Num interval = add(new Setting.Num("Interval (s)", 20, 5, 120));
        private int timer; private boolean flip;
        public AntiAfk() { super("Anti AFK", Category.DONUT); }
        @Override public void onTick() {
            if (Freecam.active) return;
            if (++timer < interval.value * 20) return;
            timer = 0; flip = !flip;
            if (mc.player.isOnGround()) mc.player.jump();
            mc.player.setYaw(mc.player.getYaw() + (flip ? 20 : -20));
            mc.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
        }
    }

    public static class PlayerAlert extends Module {
        private final Set<UUID> seen = new HashSet<>();
        private int t;
        public PlayerAlert() { super("Player Alert", Category.DONUT); }
        @Override public void onEnable() { seen.clear(); }
        @Override public void onTick() {
            if (++t % 10 != 0) return;
            Set<UUID> now = new HashSet<>();
            for (PlayerEntity p : mc.world.getPlayers()) {
                if (p == mc.player) continue;
                now.add(p.getUuid());
                if (seen.add(p.getUuid()))
                    mc.player.sendMessage(Text.literal("§c[Caleon] §f" + p.getName().getString() + " is nearby (" + (int) mc.player.distanceTo(p)
                            + "m) at X " + p.getBlockX() + " Z " + p.getBlockZ()), false);
            }
            seen.retainAll(now);
        }
    }
}
