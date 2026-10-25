package com.musicbox.client;

import com.musicbox.config.MusicBoxConfig;
import com.musicbox.music.MusicPlayer;
import com.musicbox.music.Track;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.awt.Desktop;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Main library screen.
 *
 * Layout:
 *   left  - search, tabs, song list (with a draggable splitter and scrollbar)
 *   right - album art, title/artist, volume, progress, transport controls
 *
 * The panel can be made transparent to show a user-supplied background image.
 */
public final class MusicScreen extends Screen {
    private static final int ROW_HEIGHT = 22;
    private static final int COLOR_PANEL = 0x14151D;
    private static final int COLOR_PANEL_EDGE = 0xFF3B4058;
    private static final int COLOR_LIST = 0xFF0D0E14;
    private static final int COLOR_TEXT = 0xFFF1F4FF;
    private static final int COLOR_MUTED = 0xFF98A2BE;
    private static final int COLOR_ACCENT = 0xFF62D9A6;
    private static final int COLOR_SELECTED = 0xFF263A56;
    private static final int COLOR_HOVER = 0xFF1D202C;

    private final MusicPlayer player = MusicPlayer.get();
    private final List<Track> visible = new ArrayList<>();

    private EditBox search;
    private Button close;
    private Button playPause;
    private Button previous;
    private Button next;
    private Button refresh;
    private Button folder;
    private Button backgroundButton;
    private Button hudButton;
    private Button tabAll;
    private Button tabFavorites;
    private VolumeSlider volumeSlider;

    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int listX;
    private int listY;
    private int listWidth;
    private int listHeight;
    private int listInnerWidth;
    private int rightX;
    private int rightWidth;
    private int artX;
    private int artY;
    private int artSize;
    private int progressX;
    private int progressY;
    private int progressWidth;
    private int splitterX;
    private int volumeY;

    private int scroll;
    private int selected = -1;
    private boolean favoritesTab;
    private boolean draggingProgress;
    private boolean draggingSplitter;
    private boolean draggingList;
    private boolean draggingScrollbar;
    private double dragStartX;
    private double dragStartY;
    private int dragStartListWidth;
    private int dragStartScroll;
    private double listRatio;
    private int lastClickRow = -1;
    private long lastClickAt;

    private int scrollbarX;
    private int scrollbarY;
    private int scrollbarHeight;
    private int scrollbarThumbY;
    private int scrollbarThumbHeight;

    private Identifier backgroundTexture;
    private Track artTrack;
    private Identifier artTexture;

    public MusicScreen() {
        super(Component.translatable("screen.musicbox.title"));
    }

    @Override
    protected void init() {
        player.ensureLoaded();
        listRatio = Math.max(0.35D, Math.min(0.68D, MusicBoxConfig.get().listWidthRatio));
        loadBackground();
        recalculateLayout();

        int closeWidth = 44;
        close = addRenderableWidget(Button.builder(Component.translatable("gui.musicbox.close"), button -> onClose())
                .bounds(panelX + panelWidth - closeWidth - 8, panelY + 6, closeWidth, 16)
                .build());

        search = new EditBox(font, listX, panelY + 28, listWidth, 18, Component.translatable("gui.musicbox.search"));
        search.setHint(Component.translatable("gui.musicbox.search"));
        search.setMaxLength(80);
        search.setResponder(value -> refreshVisible());
        addRenderableWidget(search);

        tabAll = addRenderableWidget(Button.builder(Component.translatable("gui.musicbox.all"), button -> {
            favoritesTab = false;
            refreshVisible();
            updateButtonStates();
        }).bounds(listX, panelY + 50, 54, 16).build());

        tabFavorites = addRenderableWidget(Button.builder(Component.translatable("gui.musicbox.favorites"), button -> {
            favoritesTab = true;
            refreshVisible();
            updateButtonStates();
        }).bounds(listX + 58, panelY + 50, 54, 16).build());

        refresh = addRenderableWidget(Button.builder(Component.translatable("gui.musicbox.refresh"), button -> {
            player.reload();
            refreshVisible();
            updateButtonStates();
        }).bounds(listX, panelY + panelHeight - 38, 64, 18).build());

        folder = addRenderableWidget(Button.builder(Component.translatable("gui.musicbox.folder"), button -> openMusicFolder())
                .bounds(listX + 68, panelY + panelHeight - 38, 64, 18)
                .build());

        backgroundButton = addRenderableWidget(Button.builder(Component.translatable("gui.musicbox.background"), button -> changeBackground())
                .bounds(listX + 136, panelY + panelHeight - 38, 64, 18)
                .build());

        hudButton = addRenderableWidget(Button.builder(Component.translatable("gui.musicbox.hud"), button -> {
            if (minecraft != null) {
                minecraft.setScreen(new HudEditorScreen(this));
            }
        }).bounds(listX + 204, panelY + panelHeight - 38, 64, 18).build());

        volumeSlider = addRenderableWidget(new VolumeSlider(rightX, panelY + panelHeight - 88, rightWidth, 16, player.volume()));

        previous = addRenderableWidget(Button.builder(Component.translatable("gui.musicbox.prev"), button -> player.previous())
                .bounds(rightX, panelY + panelHeight - 38, 52, 20)
                .build());
        playPause = addRenderableWidget(Button.builder(Component.translatable("gui.musicbox.play"), button -> onPlayPause())
                .bounds(rightX, panelY + panelHeight - 38, 72, 20)
                .build());
        next = addRenderableWidget(Button.builder(Component.translatable("gui.musicbox.next"), button -> player.next())
                .bounds(rightX, panelY + panelHeight - 38, 52, 20)
                .build());

        layoutWidgets();
        refreshArtwork();
        refreshVisible();
        updateButtonStates();

    }

    private void loadBackground() {
        Path path = MusicBoxConfig.backgroundPath();
        backgroundTexture = path == null ? null : Textures.fromFile(path, "background");
    }

    private void recalculateLayout() {
        panelWidth = Math.max(300, Math.min(640, width - 20));
        panelHeight = Math.max(220, Math.min(380, height - 20));
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;

        listX = panelX + 10;
        listY = panelY + 72;
        listInnerWidth = panelWidth - 20;
        int minList = Math.max(145, (int) (listInnerWidth * 0.35D));
        int maxList = Math.max(minList, (int) (listInnerWidth * 0.68D));
        listWidth = Math.max(minList, Math.min(maxList, (int) (listInnerWidth * listRatio)));
        listWidth = Math.min(listWidth, panelWidth - 210);
        listWidth = Math.max(minList, listWidth);
        listHeight = Math.max(40, (panelY + panelHeight - 58) - listY);

        rightX = listX + listWidth + 12;
        rightWidth = Math.max(100, panelX + panelWidth - 10 - rightX);
        splitterX = listX + listWidth + 5;

        artSize = Math.min(Math.max(40, rightWidth - 16), Math.max(54, panelHeight - 190));
        artSize = Math.max(40, Math.min(170, artSize));
        artX = rightX + (rightWidth - artSize) / 2;
        artY = panelY + 26;

        volumeY = panelY + panelHeight - 88;
        progressX = rightX + 10;
        progressWidth = Math.max(40, rightWidth - 20);
        progressY = panelY + panelHeight - 58;
    }

    private void layoutWidgets() {
        if (search == null) {
            return;
        }
        close.setX(panelX + panelWidth - 52);
        close.setY(panelY + 6);

        search.setX(listX);
        search.setY(panelY + 28);
        search.setWidth(listWidth);

        int tabWidth = Math.max(40, Math.min(54, (listWidth - 4) / 2));
        tabAll.setX(listX);
        tabAll.setY(panelY + 50);
        tabAll.setWidth(tabWidth);
        tabFavorites.setX(listX + tabWidth + 4);
        tabFavorites.setY(panelY + 50);
        tabFavorites.setWidth(tabWidth);

        int leftButtonY = panelY + panelHeight - 38;
        int leftButtonWidth = Math.max(34, Math.min(64, (listWidth - 12) / 4));
        refresh.setX(listX);
        refresh.setY(leftButtonY);
        refresh.setWidth(leftButtonWidth);
        folder.setX(listX + leftButtonWidth + 4);
        folder.setY(leftButtonY);
        folder.setWidth(leftButtonWidth);
        backgroundButton.setX(listX + (leftButtonWidth + 4) * 2);
        backgroundButton.setY(leftButtonY);
        backgroundButton.setWidth(leftButtonWidth);
        hudButton.setX(listX + (leftButtonWidth + 4) * 3);
        hudButton.setY(leftButtonY);
        hudButton.setWidth(leftButtonWidth);

        volumeSlider.setX(rightX);
        volumeSlider.setY(volumeY);
        volumeSlider.setWidth(rightWidth);

        int controlY = panelY + panelHeight - 38;
        int previousWidth = 52;
        int nextWidth = 52;
        int playWidth = Math.max(60, Math.min(84, rightWidth - 116));
        int total = previousWidth + 4 + playWidth + 4 + nextWidth;
        int start = rightX + Math.max(0, (rightWidth - total) / 2);
        previous.setX(start);
        previous.setY(controlY);
        previous.setWidth(previousWidth);
        playPause.setX(start + previousWidth + 4);
        playPause.setY(controlY);
        playPause.setWidth(playWidth);
        next.setX(start + previousWidth + 4 + playWidth + 4);
        next.setY(controlY);
        next.setWidth(nextWidth);
    }

    @Override
    public void tick() {
        super.tick();
        refreshArtwork();
        updateButtonStates();
    }

    private void refreshArtwork() {
        Track current = player.current();
        if (current == null) {
            artTrack = null;
            artTexture = null;
            return;
        }
        if (artTrack == null
                || !artTrack.path().equals(current.path())
                || !artTrack.title().equals(current.title())
                || !artTrack.artist().equals(current.artist())) {
            artTrack = current;
            artTexture = Artwork.get(current);
        }
    }

    private void refreshVisible() {
        Track keep = selected >= 0 && selected < visible.size() ? visible.get(selected) : null;
        String query = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        List<Track> source = favoritesTab ? player.library().favorites() : player.library().all();

        visible.clear();
        for (Track track : source) {
            if (query.isEmpty() || track.searchText().contains(query)) {
                visible.add(track);
            }
        }

        int restored = keep == null ? -1 : visible.indexOf(keep);
        if (restored >= 0) {
            selected = restored;
        } else if (player.current() != null) {
            int current = visible.indexOf(player.current());
            selected = current >= 0 ? current : (visible.isEmpty() ? -1 : 0);
        } else {
            selected = visible.isEmpty() ? -1 : 0;
        }
        scroll = clampScroll(scroll);
    }

    private void updateButtonStates() {
        if (playPause == null) {
            return;
        }
        playPause.setMessage(Component.translatable(player.isPlaying() ? "gui.musicbox.pause" : "gui.musicbox.play"));
        playPause.active = player.hasCurrent() || !visible.isEmpty() || !player.library().all().isEmpty();
        previous.active = player.hasCurrent();
        next.active = player.hasCurrent();
        tabAll.active = favoritesTab;
        tabFavorites.active = !favoritesTab;
    }

    private void onPlayPause() {
        if (player.hasCurrent() && (player.isPlaying() || player.isPaused())) {
            player.toggle();
            return;
        }
        if (selected >= 0 && selected < visible.size()) {
            playFromAllQueue(visible, selected);
            return;
        }
        List<Track> all = player.library().all();
        if (!all.isEmpty()) {
            player.play(all, 0);
        }
    }

    /**
     * Search and favorites only filter the visible rows; playback always uses
     * the full library order so Next/Previous do not follow a temporary search.
     */
    private void playFromAllQueue(List<Track> source, int index) {
        if (index < 0 || index >= source.size()) {
            return;
        }
        Track track = source.get(index);
        List<Track> all = player.library().all();
        int allIndex = all.indexOf(track);
        if (allIndex >= 0) {
            player.play(all, allIndex);
        } else {
            player.play(List.of(track), 0);
        }
    }

    private void openMusicFolder() {
        Path directory = MusicBoxConfig.musicDirectory();
        try {
            Files.createDirectories(directory);
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(directory.toFile());
                return;
            }
        } catch (Exception ignored) {
        }
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.literal("音乐文件夹: " + directory), false);
        }
    }

    private void changeBackground() {
        if (MusicBoxConfig.cycleBackground()) {
            loadBackground();
            if (minecraft != null && minecraft.player != null) {
                Path path = MusicBoxConfig.backgroundPath();
                String name = path == null ? "无" : path.getFileName().toString();
                minecraft.player.displayClientMessage(Component.literal("界面背景: " + name), true);
            }
            return;
        }
        openBackgroundsFolder();
    }

    private void openBackgroundsFolder() {
        Path directory = MusicBoxConfig.backgroundsDirectory();
        try {
            Files.createDirectories(directory);
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(directory.toFile());
                return;
            }
        } catch (Exception ignored) {
        }
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.literal("背景文件夹: " + directory), false);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderCustomBackground(graphics);
        renderPanel(graphics);
        renderTrackList(graphics, mouseX, mouseY);
        renderSplitter(graphics, mouseX, mouseY);
        renderNowPlaying(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderProgress(graphics);
    }

    private void renderCustomBackground(GuiGraphics graphics) {
        if (backgroundTexture != null) {
            int backgroundWidth = Textures.width(backgroundTexture);
            int backgroundHeight = Textures.height(backgroundTexture);
            graphics.blit(RenderPipelines.GUI_TEXTURED, backgroundTexture,
                    0, 0, 0.0F, 0.0F,
                    width, height,
                    backgroundWidth, backgroundHeight,
                    backgroundWidth, backgroundHeight, -1);
            int dim = (int) (Math.max(0.0D, Math.min(0.9D, MusicBoxConfig.get().backgroundDim)) * 255.0D);
            graphics.fill(0, 0, width, height, (dim << 24));
        }
    }

    private void renderPanel(GuiGraphics graphics) {
        int opacity = (int) (Math.max(0.55D, Math.min(1.0D, MusicBoxConfig.get().panelOpacity)) * 255.0D);
        int panelColor = (opacity << 24) | COLOR_PANEL;

        graphics.fill(panelX - 3, panelY - 3, panelX + panelWidth + 3, panelY + panelHeight + 3, 0x70000000);
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, panelColor);
        graphics.renderOutline(panelX, panelY, panelWidth, panelHeight, COLOR_PANEL_EDGE);

        graphics.drawString(font, getTitle(), panelX + 10, panelY + 9, COLOR_TEXT, false);
        if (panelWidth >= 380) {
            int brandX = panelX + 14 + font.width(getTitle());
            graphics.drawString(font, "MUSIC BOX", brandX, panelY + 9, 0xFF596078, false);
        }

        graphics.fill(listX - 2, listY - 2, listX + listWidth + 2, listY + listHeight + 2, COLOR_LIST);
        graphics.renderOutline(listX - 2, listY - 2, listWidth + 4, listHeight + 4, 0xFF2A2E40);
        graphics.fill(rightX - 6, panelY + 70, rightX - 5, panelY + panelHeight - 50, 0xFF2A2E40);
    }

    private void renderSplitter(GuiGraphics graphics, int mouseX, int mouseY) {
        boolean hovered = Math.abs(mouseX - splitterX) <= 4
                && mouseY >= listY && mouseY <= listY + listHeight;
        int color = draggingSplitter ? COLOR_ACCENT : (hovered ? 0xFF8D97B5 : 0xFF3A405A);
        graphics.fill(splitterX - 1, listY + 10, splitterX + 1, listY + listHeight - 10, color);
        int gripY = listY + listHeight / 2;
        graphics.fill(splitterX - 2, gripY - 7, splitterX + 2, gripY - 5, color);
        graphics.fill(splitterX - 2, gripY - 2, splitterX + 2, gripY, color);
        graphics.fill(splitterX - 2, gripY + 3, splitterX + 2, gripY + 5, color);
    }

    private void renderTrackList(GuiGraphics graphics, int mouseX, int mouseY) {
        if (visible.isEmpty()) {
            String title;
            String hint;
            if (favoritesTab) {
                title = Component.translatable("gui.musicbox.favorites_empty").getString();
                hint = Component.translatable("gui.musicbox.favorites_hint").getString();
            } else if (player.library().all().isEmpty()) {
                title = Component.translatable("gui.musicbox.empty").getString();
                hint = Component.translatable("gui.musicbox.empty_hint").getString();
            } else {
                title = Component.translatable("gui.musicbox.no_match").getString();
                hint = "";
            }
            int centerX = listX + listWidth / 2;
            graphics.drawCenteredString(font, title, centerX, listY + listHeight / 2 - 10, COLOR_MUTED);
            if (!hint.isEmpty()) {
                String clipped = font.plainSubstrByWidth(hint, listWidth - 16);
                graphics.drawCenteredString(font, clipped, centerX, listY + listHeight / 2 + 4, 0xFF687088);
            }
            scrollbarThumbHeight = 0;
            return;
        }

        graphics.enableScissor(listX, listY, listX + listWidth, listY + listHeight);
        int first = Math.max(0, scroll / ROW_HEIGHT);
        for (int row = first; row < visible.size(); row++) {
            int rowY = listY + row * ROW_HEIGHT - scroll;
            if (rowY + ROW_HEIGHT < listY) {
                continue;
            }
            if (rowY > listY + listHeight) {
                break;
            }

            Track track = visible.get(row);
            boolean hovering = mouseX >= listX && mouseX < listX + listWidth
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT
                    && mouseY >= listY && mouseY < listY + listHeight;
            boolean current = player.current() != null && player.current().path().equals(track.path());

            if (row == selected) {
                graphics.fill(listX, rowY, listX + listWidth, rowY + ROW_HEIGHT, COLOR_SELECTED);
            } else if (hovering) {
                graphics.fill(listX, rowY, listX + listWidth, rowY + ROW_HEIGHT, COLOR_HOVER);
            }
            if (current) {
                graphics.fill(listX, rowY, listX + 2, rowY + ROW_HEIGHT, COLOR_ACCENT);
            }

            String index = String.format(Locale.ROOT, "%02d", row + 1);
            graphics.drawString(font, index, listX + 7, rowY + 6, current ? COLOR_ACCENT : 0xFF5D657C, false);

            int textX = listX + 29;
            int textWidth = Math.max(24, listWidth - 42);
            String title = font.plainSubstrByWidth(track.displayName(), textWidth);
            graphics.drawString(font, title, textX, rowY + 3, row == selected ? COLOR_TEXT : 0xFFD8DEF0, false);

            String secondLine = font.plainSubstrByWidth(track.fileName(), textWidth);
            graphics.drawString(font, secondLine, textX, rowY + 12, current ? 0xFF8ECFB4 : COLOR_MUTED, false);

            if (current) {
                graphics.drawString(font, "♪", listX + listWidth - 14, rowY + 7, COLOR_ACCENT, false);
            }
        }
        graphics.disableScissor();

        int contentHeight = visible.size() * ROW_HEIGHT;
        int maxScroll = Math.max(0, contentHeight - listHeight);
        scrollbarX = listX + listWidth - 5;
        scrollbarY = listY;
        scrollbarHeight = listHeight;
        if (maxScroll > 0) {
            scrollbarThumbHeight = Math.max(16, listHeight * listHeight / contentHeight);
            scrollbarThumbY = listY + (int) ((listHeight - scrollbarThumbHeight) * (scroll / (double) maxScroll));
            graphics.fill(scrollbarX, listY, listX + listWidth, listY + listHeight, 0x50000000);
            graphics.fill(scrollbarX, scrollbarThumbY, listX + listWidth, scrollbarThumbY + scrollbarThumbHeight, 0xFF59617A);
        } else {
            scrollbarThumbHeight = 0;
        }
    }

    private void renderNowPlaying(GuiGraphics graphics) {
        Track track = player.current();
        drawAlbumArt(graphics);

        int textY = artY + artSize + 8;
        int centerX = rightX + rightWidth / 2;
        if (track == null) {
            graphics.drawCenteredString(font, Component.translatable("gui.musicbox.idle"), centerX, textY, COLOR_MUTED);
            graphics.drawCenteredString(font, Component.translatable("gui.musicbox.idle_hint"), centerX, textY + 13, 0xFF687088);
            return;
        }

        String title = font.plainSubstrByWidth(track.displayName(), rightWidth - 4);
        graphics.drawCenteredString(font, title, centerX, textY, COLOR_TEXT);
        String artist = font.plainSubstrByWidth(track.artist().isBlank() ? track.fileName() : track.artist(), rightWidth - 4);
        graphics.drawCenteredString(font, artist, centerX, textY + 13, COLOR_MUTED);

        if (textY + 27 < volumeY - 6) {
            String status;
            int color;
            if (player.isPlaying()) {
                status = Component.translatable("gui.musicbox.playing").getString();
                color = COLOR_ACCENT;
            } else if (player.isPaused()) {
                status = Component.translatable("gui.musicbox.paused").getString();
                color = 0xFFE8C46A;
            } else {
                status = Component.translatable("gui.musicbox.stopped").getString();
                color = 0xFF8D97B5;
            }
            graphics.drawCenteredString(font, status, centerX, textY + 27, color);
        }
    }

    private void drawAlbumArt(GuiGraphics graphics) {
        if (artTexture != null) {
            int textureWidth = Textures.width(artTexture);
            int textureHeight = Textures.height(artTexture);
            graphics.blit(RenderPipelines.GUI_TEXTURED, artTexture,
                    artX, artY, 0.0F, 0.0F,
                    artSize, artSize,
                    textureWidth, textureHeight,
                    textureWidth, textureHeight, -1);
        } else {
            graphics.fill(artX, artY, artX + artSize, artY + artSize, 0xFF1A1D2B);
            graphics.fillGradient(artX, artY, artX + artSize, artY + artSize, 0xFF20283A, 0xFF101219);
            graphics.drawCenteredString(font, "♪", artX + artSize / 2, artY + artSize / 2 - 7, COLOR_ACCENT);
            String noCover = font.plainSubstrByWidth(Component.translatable("gui.musicbox.no_cover").getString(), artSize - 6);
            graphics.drawCenteredString(font, noCover, artX + artSize / 2, artY + artSize / 2 + 7, 0xFF687088);
        }
        graphics.renderOutline(artX - 1, artY - 1, artSize + 2, artSize + 2, 0xFF3A405A);
    }

    private void renderProgress(GuiGraphics graphics) {
        int height = 5;
        long position = player.positionMs();
        long duration = player.durationMs();
        double fraction = duration > 0L ? Math.max(0.0D, Math.min(1.0D, position / (double) duration)) : 0.0D;

        graphics.fill(progressX, progressY, progressX + progressWidth, progressY + height, 0x70000000);
        graphics.fill(progressX, progressY, progressX + (int) (progressWidth * fraction), progressY + height, COLOR_ACCENT);
        int handleX = progressX + (int) (progressWidth * fraction);
        graphics.fill(handleX - 1, progressY - 2, handleX + 1, progressY + height + 2, 0xFFE9FFF5);

        String elapsed = formatTime(position);
        String total = duration > 0L ? formatTime(duration) : "--:--";
        graphics.drawString(font, elapsed, progressX, progressY - 11, 0xFFB7C0D8, false);
        graphics.drawString(font, total, progressX + progressWidth - font.width(total), progressY - 11, 0xFFB7C0D8, false);
    }

    private static String formatTime(long millis) {
        long seconds = Math.max(0L, millis / 1000L);
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60L, seconds % 60L);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (super.mouseClicked(event, doubled)) {
            return true;
        }
        if (event.button() != 0) {
            return false;
        }

        double mouseX = event.x();
        double mouseY = event.y();

        if (isOverSplitter(mouseX, mouseY)) {
            draggingSplitter = true;
            dragStartX = mouseX;
            dragStartListWidth = listWidth;
            return true;
        }

        if (isOverScrollbar(mouseX, mouseY)) {
            draggingScrollbar = true;
            dragScrollbar(mouseY);
            return true;
        }

        if (isOverProgress(mouseX, mouseY)) {
            draggingProgress = true;
            seekAt(mouseX);
            return true;
        }

        if (mouseX >= listX && mouseX < listX + listWidth && mouseY >= listY && mouseY < listY + listHeight) {
            int row = (int) ((mouseY - listY + scroll) / ROW_HEIGHT);
            if (row >= 0 && row < visible.size()) {
                selected = row;
                draggingList = true;
                dragStartY = mouseY;
                dragStartScroll = scroll;

                long now = System.currentTimeMillis();
                if (lastClickRow == row && now - lastClickAt < 350L) {
                    playFromAllQueue(visible, row);
                    lastClickRow = -1;
                } else {
                    lastClickRow = row;
                    lastClickAt = now;
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (draggingSplitter) {
            int minList = Math.max(145, (int) (listInnerWidth * 0.35D));
            int maxList = Math.max(minList, (int) (listInnerWidth * 0.68D));
            int requested = dragStartListWidth + (int) Math.round(event.x() - dragStartX);
            requested = Math.max(minList, Math.min(maxList, requested));
            requested = Math.min(requested, panelWidth - 210);
            listRatio = requested / (double) listInnerWidth;
            recalculateLayout();
            layoutWidgets();
            scroll = clampScroll(scroll);
            return true;
        }
        if (draggingScrollbar) {
            dragScrollbar(event.y());
            return true;
        }
        if (draggingProgress) {
            seekAt(event.x());
            return true;
        }
        if (draggingList) {
            int delta = (int) Math.round(event.y() - dragStartY);
            if (Math.abs(delta) > 2) {
                scroll = clampScroll(dragStartScroll - delta);
            }
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        boolean handled = draggingProgress || draggingSplitter || draggingList || draggingScrollbar;
        if (draggingSplitter) {
            MusicBoxConfig.get().listWidthRatio = listRatio;
            MusicBoxConfig.save();
        }
        draggingProgress = false;
        draggingSplitter = false;
        draggingList = false;
        draggingScrollbar = false;
        if (handled) {
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= listX && mouseX < listX + listWidth && mouseY >= listY && mouseY < listY + listHeight) {
            scroll = clampScroll(scroll - (int) (scrollY * ROW_HEIGHT * 2));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (search != null && search.isFocused()) {
            return super.keyPressed(event);
        }
        if (event.key() == GLFW.GLFW_KEY_SPACE) {
            player.toggle();
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_UP) {
            moveSelection(-1);
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_DOWN) {
            moveSelection(1);
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
            if (selected >= 0 && selected < visible.size()) {
                playFromAllQueue(visible, selected);
            }
            return true;
        }
        return super.keyPressed(event);
    }

    private void moveSelection(int delta) {
        if (visible.isEmpty()) {
            return;
        }
        selected = Math.max(0, Math.min(visible.size() - 1, selected + delta));
        int rowTop = selected * ROW_HEIGHT;
        if (rowTop < scroll) {
            scroll = rowTop;
        } else if (rowTop + ROW_HEIGHT > scroll + listHeight) {
            scroll = rowTop + ROW_HEIGHT - listHeight;
        }
        scroll = clampScroll(scroll);
    }

    private int clampScroll(int value) {
        int max = Math.max(0, visible.size() * ROW_HEIGHT - listHeight);
        return Math.max(0, Math.min(max, value));
    }

    private boolean isOverSplitter(double mouseX, double mouseY) {
        return Math.abs(mouseX - splitterX) <= 4
                && mouseY >= listY && mouseY <= listY + listHeight;
    }

    private boolean isOverScrollbar(double mouseX, double mouseY) {
        if (scrollbarThumbHeight <= 0) {
            return false;
        }
        return mouseX >= scrollbarX - 2 && mouseX <= scrollbarX + 5
                && mouseY >= scrollbarY && mouseY <= scrollbarY + scrollbarHeight;
    }

    private void dragScrollbar(double mouseY) {
        int maxScroll = Math.max(0, visible.size() * ROW_HEIGHT - listHeight);
        if (maxScroll <= 0 || scrollbarThumbHeight <= 0) {
            return;
        }
        int movable = Math.max(1, scrollbarHeight - scrollbarThumbHeight);
        double fraction = (mouseY - scrollbarY - scrollbarThumbHeight / 2.0D) / movable;
        fraction = Math.max(0.0D, Math.min(1.0D, fraction));
        scroll = (int) Math.round(fraction * maxScroll);
    }

    private boolean isOverProgress(double mouseX, double mouseY) {
        return mouseX >= progressX && mouseX <= progressX + progressWidth
                && mouseY >= progressY - 5 && mouseY <= progressY + 10;
    }

    private void seekAt(double mouseX) {
        double fraction = (mouseX - progressX) / Math.max(1, progressWidth);
        player.seekFraction(fraction);
    }

    @Override
    public void onClose() {
        MusicBoxConfig.get().listWidthRatio = listRatio;
        MusicBoxConfig.save();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
