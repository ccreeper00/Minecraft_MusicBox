package com.musicbox.client;

import com.musicbox.config.MusicBoxConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Drag to move and resize the now-playing HUD; the layout is stored as ratios/scale. */
public final class HudEditorScreen extends Screen {
    private static final int COLOR_ACCENT = 0xFF62D9A6;

    private final Screen parent;
    private boolean dragging;
    private boolean resizing;
    private double dragOffsetX;
    private double dragOffsetY;

    public HudEditorScreen(Screen parent) {
        super(Component.translatable("screen.musicbox.hud_title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int center = width / 2;
        addRenderableWidget(Button.builder(Component.translatable("gui.musicbox.hud_done"), button -> onClose())
                .bounds(center - 120, height - 28, 88, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.musicbox.hud_reset"), button -> resetPosition())
                .bounds(center - 28, height - 28, 88, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal("-"), button -> adjustScale(-0.1D))
                .bounds(center + 64, height - 28, 24, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal("+"), button -> adjustScale(0.1D))
                .bounds(center + 92, height - 28, 24, 20)
                .build());
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderTransparentBackground(graphics);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.drawCenteredString(font, getTitle(), width / 2, 10, 0xFFF1F4FF);
        graphics.drawCenteredString(font, Component.translatable("gui.musicbox.hud_hint"),
                width / 2, 23, 0xFF98A2BE);

        int hudX = NowPlayingHud.x(graphics);
        int hudY = NowPlayingHud.y(graphics);
        int hudWidth = NowPlayingHud.scaledWidth();
        int hudHeight = NowPlayingHud.scaledHeight();
        NowPlayingHud.render(graphics, hudX, hudY, true);

        // Bottom-right resize grip.
        int right = hudX + hudWidth;
        int bottom = hudY + hudHeight;
        graphics.fill(right - 11, bottom - 2, right - 2, bottom - 1, COLOR_ACCENT);
        graphics.fill(right - 2, bottom - 11, right - 1, bottom - 2, COLOR_ACCENT);
        graphics.fill(right - 7, bottom - 7, right - 2, bottom - 4, COLOR_ACCENT);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private boolean overHud(double mouseX, double mouseY) {
        int hudX = NowPlayingHud.x(width);
        int hudY = NowPlayingHud.y(height);
        return mouseX >= hudX && mouseX < hudX + NowPlayingHud.scaledWidth()
                && mouseY >= hudY && mouseY < hudY + NowPlayingHud.scaledHeight();
    }

    private boolean overGrip(double mouseX, double mouseY) {
        int right = NowPlayingHud.x(width) + NowPlayingHud.scaledWidth();
        int bottom = NowPlayingHud.y(height) + NowPlayingHud.scaledHeight();
        return mouseX >= right - 13 && mouseX <= right + 2
                && mouseY >= bottom - 13 && mouseY <= bottom + 2;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (super.mouseClicked(event, doubled)) {
            return true;
        }
        if (event.button() != 0) {
            return false;
        }
        if (overGrip(event.x(), event.y())) {
            resizing = true;
            return true;
        }
        if (overHud(event.x(), event.y())) {
            dragging = true;
            dragOffsetX = event.x() - NowPlayingHud.x(width);
            dragOffsetY = event.y() - NowPlayingHud.y(height);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        MusicBoxConfig config = MusicBoxConfig.get();
        if (resizing) {
            double newScale = (event.x() - NowPlayingHud.x(width)) / NowPlayingHud.BASE_WIDTH;
            config.hudScale = Math.max(0.6D, Math.min(2.0D, newScale));
            return true;
        }
        if (dragging) {
            config.hudX = Math.max(0.0D, Math.min(1.0D, (event.x() - dragOffsetX) / width));
            config.hudY = Math.max(0.0D, Math.min(1.0D, (event.y() - dragOffsetY) / height));
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging || resizing) {
            dragging = false;
            resizing = false;
            MusicBoxConfig.save();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (overHud(mouseX, mouseY) || overGrip(mouseX, mouseY)) {
            adjustScale(scrollY > 0 ? 0.1D : -0.1D);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_R) {
            resetPosition();
            return true;
        }
        return super.keyPressed(event);
    }

    private void adjustScale(double delta) {
        MusicBoxConfig config = MusicBoxConfig.get();
        config.hudScale = Math.max(0.6D, Math.min(2.0D, config.hudScale + delta));
        MusicBoxConfig.save();
    }

    private void resetPosition() {
        MusicBoxConfig config = MusicBoxConfig.get();
        config.hudX = 0.02D;
        config.hudY = 0.10D;
        config.hudScale = 1.0D;
        MusicBoxConfig.save();
    }

    @Override
    public void onClose() {
        MusicBoxConfig.save();
        if (minecraft != null && parent != null) {
            minecraft.setScreen(parent);
        } else {
            super.onClose();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
