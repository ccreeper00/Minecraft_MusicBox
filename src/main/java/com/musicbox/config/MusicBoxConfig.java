package com.musicbox.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.musicbox.MusicBox;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Small JSON config. Kept deliberately simple so it can be edited by hand.
 */
public final class MusicBoxConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static MusicBoxConfig instance;

    /** Relative paths are resolved against the game directory. */
    public String musicFolder = "musicbox";
    /** 0.0 - 1.0 */
    public double volume = 0.7D;
    /** Show an action-bar line when a new track starts. */
    public boolean showNowPlaying = true;
    /** Optional custom GUI background. Relative paths use the game directory. */
    public String backgroundImage = "";
    /** 0.0 - 0.9, dark overlay over the background image. */
    public double backgroundDim = 0.35D;
    /** 0.55 - 1.0, opacity of the main panel. */
    public double panelOpacity = 0.82D;
    /** 0.35 - 0.68, width of the song list relative to the panel. */
    public double listWidthRatio = 0.55D;
    /** Show the in-game now-playing HUD. */
    public Boolean showHud = Boolean.TRUE;
    /** HUD top-left position as a fraction of the scaled screen (0.0 - 1.0). */
    public double hudX = 0.02D;
    public double hudY = 0.10D;
    /** HUD scale, 0.6 - 2.0. */
    public double hudScale = 1.0D;

    private MusicBoxConfig() {
    }

    public static synchronized MusicBoxConfig get() {
        if (instance == null) {
            load();
        }
        return instance;
    }

    public static synchronized void load() {
        Path file = configFile();
        MusicBoxConfig loaded = new MusicBoxConfig();
        if (Files.isRegularFile(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                MusicBoxConfig fromDisk = GSON.fromJson(reader, MusicBoxConfig.class);
                if (fromDisk != null) {
                    loaded = fromDisk;
                }
            } catch (Exception exception) {
                MusicBox.LOGGER.warn("Could not read {}, using defaults", file, exception);
            }
        }

        if (loaded.musicFolder == null || loaded.musicFolder.isBlank()) {
            loaded.musicFolder = "musicbox";
        }
        loaded.volume = Math.max(0.0D, Math.min(1.0D, loaded.volume));
        if (loaded.backgroundImage == null) {
            loaded.backgroundImage = "";
        }
        loaded.backgroundDim = Math.max(0.0D, Math.min(0.9D, loaded.backgroundDim));
        loaded.panelOpacity = Math.max(0.55D, Math.min(1.0D, loaded.panelOpacity));
        loaded.listWidthRatio = Math.max(0.35D, Math.min(0.68D, loaded.listWidthRatio));
        if (loaded.showHud == null) {
            loaded.showHud = Boolean.TRUE;
        }
        loaded.hudX = Math.max(0.0D, Math.min(1.0D, loaded.hudX));
        loaded.hudY = Math.max(0.0D, Math.min(1.0D, loaded.hudY));
        if (loaded.hudScale <= 0.0D) {
            loaded.hudScale = 1.0D;
        }
        loaded.hudScale = Math.max(0.6D, Math.min(2.0D, loaded.hudScale));
        instance = loaded;

        try {
            Files.createDirectories(configFile().getParent());
            Files.createDirectories(musicDirectory());
            createWelcomeFile();
        } catch (IOException exception) {
            MusicBox.LOGGER.warn("Could not create MusicBox folders", exception);
        }

        save();
    }

    public static synchronized void save() {
        if (instance == null) {
            return;
        }
        Path file = configFile();
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(instance, writer);
            }
        } catch (IOException exception) {
            MusicBox.LOGGER.warn("Could not save {}", file, exception);
        }
    }

    public static Path configFile() {
        return FMLPaths.CONFIGDIR.get().resolve("musicbox.json");
    }

    public static Path musicDirectory() {
        String folder = get().musicFolder;
        try {
            Path path = Path.of(folder);
            if (!path.isAbsolute()) {
                path = FMLPaths.GAMEDIR.get().resolve(path);
            }
            return path.normalize();
        } catch (InvalidPathException exception) {
            MusicBox.LOGGER.warn("Invalid musicFolder '{}', falling back to musicbox", folder);
            return FMLPaths.GAMEDIR.get().resolve("musicbox");
        }
    }

    /**
     * All usable backgrounds, in display order:
     * musicbox/background.* first, then musicbox/backgrounds/*.
     */
    public static List<Path> backgroundCandidates() {
        Path directory = musicDirectory();
        List<Path> result = new ArrayList<>();
        for (String name : new String[]{"background.png", "background.jpg", "background.jpeg"}) {
            Path path = directory.resolve(name);
            if (Files.isRegularFile(path)) {
                result.add(path.toAbsolutePath().normalize());
            }
        }

        Path backgrounds = directory.resolve("backgrounds");
        if (Files.isDirectory(backgrounds)) {
            try (Stream<Path> stream = Files.list(backgrounds)) {
                stream.filter(Files::isRegularFile)
                        .filter(MusicBoxConfig::isImage)
                        .sorted(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                        .map(path -> path.toAbsolutePath().normalize())
                        .forEach(result::add);
            } catch (IOException exception) {
                MusicBox.LOGGER.warn("Could not scan backgrounds folder {}", backgrounds, exception);
            }
        }
        return result;
    }

    /**
     * Resolves the configured background image, or automatically uses the
     * first image from {@link #backgroundCandidates()}.
     */
    public static Path backgroundPath() {
        String configured = get().backgroundImage;
        if (configured != null && !configured.isBlank()) {
            try {
                Path path = Path.of(configured);
                if (!path.isAbsolute()) {
                    path = FMLPaths.GAMEDIR.get().resolve(path);
                }
                path = path.normalize();
                if (Files.isRegularFile(path)) {
                    return path;
                }
            } catch (InvalidPathException exception) {
                MusicBox.LOGGER.warn("Invalid backgroundImage '{}'", configured);
            }
        }

        List<Path> candidates = backgroundCandidates();
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    /**
     * Selects the next image in the backgrounds folder and saves it.
     * Returns false when there is no image to select.
     */
    public static synchronized boolean cycleBackground() {
        List<Path> candidates = backgroundCandidates();
        if (candidates.isEmpty()) {
            return false;
        }
        Path current = backgroundPath();
        int index = current == null ? -1 : candidates.indexOf(current.toAbsolutePath().normalize());
        Path next = candidates.get((index + 1) % candidates.size());
        get().backgroundImage = next.toString();
        save();
        return true;
    }

    public static Path backgroundsDirectory() {
        return musicDirectory().resolve("backgrounds");
    }

    private static boolean isImage(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg");
    }

    private static void createWelcomeFile() throws IOException {
        Path directory = musicDirectory();
        Path readme = directory.resolve("README.txt");
        if (Files.notExists(readme)) {
            Files.writeString(readme, """
                    悦音盒 MusicBox
                    ===============

                    把 .mp3 / .ogg / .wav 文件放进这个文件夹（可以建子文件夹）。
                    游戏内按 M 打开选曲界面，点“刷新”重新扫描。

                    收藏列表：
                    在音乐文件夹里创建 favorites.m3u，一行一个文件。例如：
                      #EXTM3U
                      Artist - Song.mp3
                      D:\\Music\\Another Song.ogg

                    相对路径按本文件夹解析，也可以写绝对路径。
                    本模组不会连接 QQ 音乐，也不包含任何 DRM 解密功能。

                    界面背景：
                    把任意 .png / .jpg / .jpeg 放进 backgrounds 文件夹即可；
                    有多张时，在界面左下角点“背景”切换。
                    """, StandardCharsets.UTF_8);
        }

        Path backgrounds = directory.resolve("backgrounds");
        Files.createDirectories(backgrounds);
        Path backgroundReadme = backgrounds.resolve("README.txt");
        if (Files.notExists(backgroundReadme)) {
            Files.writeString(backgroundReadme, """
                    把任意 .png / .jpg / .jpeg 图片放进这个文件夹。
                    游戏内按 M 打开界面，点左下角“背景”即可循环切换。
                    文件名不限；多张按文件名排序。
                    """, StandardCharsets.UTF_8);
        }
    }
}
