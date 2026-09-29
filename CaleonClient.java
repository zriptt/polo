package com.caleon.client;

import com.caleon.client.gui.ClickGuiScreen;
import com.caleon.client.module.DonutModules;
import com.caleon.client.module.Module;
import com.caleon.client.module.ModuleManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class CaleonClient implements ClientModInitializer {
    private static final int RED = ClickGuiScreen.ACCENT;
    private static KeyBinding openGui;

    @Override
    public void onInitializeClient() {
        ModuleManager.init();
        openGui = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.caleonclient.gui", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, "category.caleonclient"));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (openGui.wasPressed()) if (mc.currentScreen == null) mc.setScreen(new ClickGuiScreen());
            ModuleManager.tick();
        });

        WorldRenderEvents.LAST.register(ctx -> {
            var ms = ctx.matrixStack();
            if (ms == null) return;
            Camera cam = ctx.camera();
            double yaw = Math.toRadians(cam.getYaw()), pitch = Math.toRadians(cam.getPitch());
            Vec3d look = new Vec3d(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
            ModuleManager.render(ms.peek().getPositionMatrix(), cam.getPos(), look);
        });

        HudRenderCallback.EVENT.register((ctx, tick) -> {
            var hud = ModuleManager.hud;
            if (hud == null || !hud.enabled) return;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.options.hudHidden) return;
            var tr = mc.textRenderer;
            int y = 4;
            if (hud.watermark.value) {
                ctx.fill(2, 2, 4, 13, RED);
                ctx.drawTextWithShadow(tr, "Caleon Client", 7, 4, RED);
                y = 17;
            }
            if (hud.info.value && mc.player != null) {
                ctx.drawTextWithShadow(tr, "XYZ " + mc.player.getBlockX() + " " + mc.player.getBlockY() + " " + mc.player.getBlockZ(), 4, y, 0xFFFFC0C0);
                ctx.drawTextWithShadow(tr, mc.getCurrentFps() + " FPS", 4, y + 10, 0xFFFFC0C0);
                y += 20;
                var sf = DonutModules.spawnerFinder;
                if (sf != null && sf.enabled) ctx.drawTextWithShadow(tr, "Spawners loaded: " + sf.count, 4, y, 0xFFFF77DD);
            }
            if (hud.arrayList.value) {
                List<Module> on = new ArrayList<>();
                for (Module m : ModuleManager.MODULES) if (m.enabled && m != hud) on.add(m);
                on.sort(Comparator.comparingInt((Module m) -> -tr.getWidth(m.name)));
                int ry = 4, w = ctx.getScaledWindowWidth();
                for (Module m : on) {
                    int tw = tr.getWidth(m.name);
                    ctx.fill(w - 2, ry - 1, w, ry + 9, RED);
                    ctx.drawTextWithShadow(tr, m.name, w - tw - 5, ry, 0xFFFF7B86);
                    ry += 10;
                }
            }
        });
    }
}
