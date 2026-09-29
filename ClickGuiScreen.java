package com.caleon.client.gui;

import com.caleon.client.module.Category;
import com.caleon.client.module.Module;
import com.caleon.client.module.ModuleManager;
import com.caleon.client.module.Setting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class ClickGuiScreen extends Screen {
    private static final int W = 108, ROW = 13, HEAD = 18, GAP = 8, TOP = 10;
    public static final int ACCENT = 0xFFFF2D3D;
    private static final int ACCENT_DIM = 0x55FF2D3D;
    private static int scroll;

    private record Hit(int x, int y, Module m, Setting s) {}

    public ClickGuiScreen() { super(Text.literal("Caleon Client")); }

    @Override public boolean shouldPause() { return false; }

    private int colX(int i) { return 10 + i * (W + GAP); }

    private int contentHeight() {
        int best = 0;
        for (Category c : Category.values()) {
            int h = 0;
            for (Module m : ModuleManager.MODULES) {
                if (m.category != c) continue;
                h += ROW;
                if (m.expanded) h += ROW * m.settings.size();
            }
            best = Math.max(best, h);
        }
        return best;
    }

    private void clampScroll() {
        int min = Math.min(0, height - (TOP + HEAD) - contentHeight() - 24);
        scroll = Math.max(min, Math.min(0, scroll));
    }

    private List<Hit> hits() {
        List<Hit> out = new ArrayList<>();
        int i = 0;
        for (Category c : Category.values()) {
            int x = colX(i++), y = TOP + HEAD + scroll;
            for (Module m : ModuleManager.MODULES) {
                if (m.category != c) continue;
                out.add(new Hit(x, y, m, null)); y += ROW;
                if (m.expanded) for (Setting s : m.settings) { out.add(new Hit(x, y, m, s)); y += ROW; }
            }
        }
        return out;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fillGradient(0, 0, width, height, 0xB0200008, 0xE0050001);
        clampScroll();
        for (Hit h : hits()) {
            if (h.y + ROW < TOP + HEAD || h.y > height) continue;
            boolean over = mouseY >= TOP + HEAD && mouseX >= h.x && mouseX < h.x + W && mouseY >= h.y && mouseY < h.y + ROW;
            ctx.fill(h.x, h.y, h.x + W, h.y + ROW, over ? 0xFF33151A : 0xFF1E0E11);
            if (h.s == null) {
                if (h.m.enabled) { ctx.fill(h.x, h.y, h.x + 2, h.y + ROW, ACCENT); ctx.fill(h.x + 2, h.y, h.x + W, h.y + ROW, 0x33FF2D3D); }
                ctx.drawTextWithShadow(textRenderer, h.m.name, h.x + 6, h.y + 3, h.m.enabled ? ACCENT : 0xFFD8CCCC);
                if (!h.m.settings.isEmpty())
                    ctx.drawTextWithShadow(textRenderer, h.m.expanded ? "-" : "+", h.x + W - 10, h.y + 3, 0xFF996666);
            } else if (h.s instanceof Setting.Bool b) {
                ctx.fill(h.x, h.y, h.x + W, h.y + ROW, 0xFF120809);
                ctx.drawTextWithShadow(textRenderer, b.name, h.x + 8, h.y + 3, 0xFFBBA0A0);
                ctx.drawTextWithShadow(textRenderer, b.value ? "ON" : "OFF", h.x + W - 22, h.y + 3, b.value ? ACCENT : 0xFF775555);
            } else if (h.s instanceof Setting.Num n) {
                ctx.fill(h.x, h.y, h.x + W, h.y + ROW, 0xFF120809);
                int fillW = (int) (W * (n.value - n.min) / (n.max - n.min));
                ctx.fill(h.x, h.y, h.x + fillW, h.y + ROW, ACCENT_DIM);
                ctx.drawTextWithShadow(textRenderer, n.name, h.x + 8, h.y + 3, 0xFFBBA0A0);
                String v = String.format("%.1f", n.value);
                ctx.drawTextWithShadow(textRenderer, v, h.x + W - 4 - textRenderer.getWidth(v), h.y + 3, 0xFFFFFFFF);
            }
        }
        // headers drawn last so scrolled rows slide under them
        int i = 0;
        for (Category c : Category.values()) {
            int x = colX(i++);
            ctx.fill(x, TOP - 2, x + W, TOP + HEAD, 0xFF2A0A10);
            ctx.fill(x, TOP + HEAD - 2, x + W, TOP + HEAD, ACCENT);
            ctx.drawTextWithShadow(textRenderer, c.label, x + 6, TOP + 4, ACCENT);
        }
        ctx.drawTextWithShadow(textRenderer, "Caleon Client  |  left click: toggle  |  right click: settings  |  scroll: move  |  RShift: close",
                10, height - 12, 0xFF884444);
    }

    private void handle(double mx, double my, int button) {
        if (my < TOP + HEAD) return;
        for (Hit h : hits()) {
            if (mx < h.x || mx >= h.x + W || my < h.y || my >= h.y + ROW) continue;
            if (h.s == null) {
                if (button == 0) h.m.toggle();
                else if (button == 1 && !h.m.settings.isEmpty()) h.m.expanded = !h.m.expanded;
            } else if (h.s instanceof Setting.Bool b) {
                if (button == 0) b.value = !b.value;
            } else if (h.s instanceof Setting.Num n) {
                double t = Math.max(0, Math.min(1, (mx - h.x) / W));
                n.value = Math.round((n.min + t * (n.max - n.min)) * 10.0) / 10.0;
            }
            return;
        }
    }

    @Override public boolean mouseClicked(double mx, double my, int button) { handle(mx, my, button); return true; }

    @Override public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        scroll += (int) (vertical * 24);
        clampScroll();
        return true;
    }

    @Override public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (button == 0) {
            for (Hit h : hits()) if (h.s instanceof Setting.Num && my >= h.y && my < h.y + ROW && my >= TOP + HEAD && mx >= h.x - 30 && mx < h.x + W + 30) {
                handle(Math.max(h.x, Math.min(h.x + W - 1, mx)), my, 0); break;
            }
        }
        return true;
    }

    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT) { close(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
