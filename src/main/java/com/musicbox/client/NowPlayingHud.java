package com.musicbox.client;

import com.musicbox.config.MusicBoxConfig;
import com.musicbox.music.MusicPlayer;
import com.musicbox.music.Track;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.Locale;

/** Compact now-playing HUD, shared by the in-game layer and the positioning editor. */
final class NowPlayingHud {
    static final int BASE_WIDTH = 200;
    static final int BASE_HEIGHT = 48;

    private static final int COLOR_BACKGROUND = 0xD0101218;
    private static final int COLOR_BORDER = 0xFF3A405A;
    private static final int COLOR_TEXT = 0xFFF1F4FF;
    private static final int COLOR_MUTED = 0xFF98A2BE;
    private static final int COLOR_ACCENT = 0xFF62D9A6;

    private static Track artTrack;
    private static Identifier artTexture;

    private NowPlayingHud() {
    }

    static double scale() {
        return Math.max(0.6D, Math.min(2.0D, MusicBoxConfig.get().hudScale));
    }

    static int scaledWidth() {
        return (int) Math.round(BASE_WIDTH * scale());
    }

    static int scaledHeight() {
        return (int) Math.round(BASE_HEIGHT * scale());
    }

    static void renderDefault(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        MusicBoxConfig config = MusicBoxConfig.get();
        if (minecraft.screen != null
                || minecraft.player == null
                || minecraft.options.hideGui
                || !Boolean.TRUE.equals(config.showHud)) {
            return;
        }
        Track track = MusicPlayer.get().current();
        if (track == null) {
            return;
        }
        render(graphics, x(graphics), y(graphics), false);
    }

    static int x(GuiGraphics graphics) {
        return x(graphics.guiWidth());
    }

    static int y(GuiGraphics graphics) {
        return y(graphics.guiHeight());
    }

    static int x(int screenWidth) {
        int max = Math.max(0, screenWidth - scaledWidth());
        return Math.max(0, Math.min(max, (int) Math.round(screenWidth * MusicBoxConfig.get().hudX)));
    }

    static int y(int screenHeight) {
        int max = Math.max(0, screenHeight - scaledHeight());
        return Math.max(0, Math.min(max, (int) Math.round(screenHeight * MusicBoxConfig.get().hudY)));
    }

    static void render(GuiGraphics graphics, int x, int y, boolean preview) {
        float scale = (float) scale();
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(scale, scale);
        renderContents(graphics, preview);
        graphics.pose().popMatrix();
    }

    private static void renderContents(GuiGraphics graphics, boolean preview) {
        MusicPlayer player = MusicPlayer.get();
        Track track = player.current();
        Font font = Minecraft.getInstance().font;

        graphics.fill(0, 0, BASE_WIDTH, BASE_HEIGHT, COLOR_BACKGROUND);
        graphics.renderOutline(0, 0, BASE_WIDTH, BASE_HEIGHT, preview ? COLOR_ACCENT : COLOR_BORDER);

        if (track == null) {
            if (preview) {
                graphics.drawCenteredString(font, Component.translatable("gui.musicbox.hud_empty"),
                        BASE_WIDTH / 2, BASE_HEIGHT / 2 - 4, COLOR_MUTED);
            }
            return;
        }

        drawArt(graphics, track, 5, 6, 36);

        int textX = 47;
        int textWidth = BASE_WIDTH - 53;
        String title = font.plainSubstrByWidth(track.displayName(), textWidth);
        graphics.drawString(font, title, textX, 6, COLOR_TEXT, false);
        String artist = font.plainSubstrByWidth(track.artist().isBlank() ? track.fileName() : track.artist(),
                textWidth);
        graphics.drawString(font, artist, textX, 18, COLOR_MUTED, false);

        String state = player.isPlaying()
                ? Component.translatable("gui.musicbox.playing").getString()
                : player.isPaused()
                ? Component.translatable("gui.musicbox.paused").getString()
                : Component.translatable("gui.musicbox.stopped").getString();
        int stateColor = player.isPlaying() ? COLOR_ACCENT : player.isPaused() ? 0xFFE8C46A : COLOR_MUTED;
        graphics.drawString(font, state, textX, 30, stateColor, false);

        long position = player.positionMs();
        long duration = player.durationMs();
        String time = formatTime(position) + " / " + (duration > 0 ? formatTime(duration) : "--:--");
        graphics.drawString(font, time, BASE_WIDTH - 6 - font.width(time), 30, COLOR_MUTED, false);

        graphics.fill(textX, 42, BASE_WIDTH - 6, 44, 0x70000000);
        double fraction = duration > 0L
                ? Math.max(0.0D, Math.min(1.0D, position / (double) duration))
                : 0.0D;
        graphics.fill(textX, 42, textX + (int) ((BASE_WIDTH - 53) * fraction), 44, COLOR_ACCENT);
    }

    private static void drawArt(GuiGraphics graphics, Track track, int x, int y, int size) {
        if (artTrack == null
                || !artTrack.path().equals(track.path())
                || !artTrack.title().equals(track.title())
                || !artTrack.artist().equals(track.artist())) {
            artTrack = track;
            artTexture = Artwork.get(track);
        }

        if (artTexture != null) {
            int textureWidth = Textures.width(artTexture);
            int textureHeight = Textures.height(artTexture);
            graphics.blit(RenderPipelines.GUI_TEXTURED, artTexture,
                    x, y, 0.0F, 0.0F,
                    size, size,
                    textureWidth, textureHeight,
                    textureWidth, textureHeight, -1);
        } else {
            graphics.fill(x, y, x + size, y + size, 0xFF1A1D2B);
            graphics.renderOutline(x, y, size, size, COLOR_BORDER);
            graphics.drawCenteredString(Minecraft.getInstance().font, "♪", x + size / 2, y + size / 2 - 4, COLOR_ACCENT);
        }
    }

    private static String formatTime(long millis) {
        long seconds = Math.max(0L, millis / 1000L);
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60L, seconds % 60L);
    }
}
