package com.musicbox.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.musicbox.MusicBox;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Loads external PNG/JPEG files into GPU textures once and caches them by
 * path + modification time. All methods must be called from the client thread.
 */
final class Textures {
    private static final Map<String, Identifier> CACHE = new HashMap<>();
    private static final Map<Identifier, int[]> SIZES = new HashMap<>();
    private static final long MAX_FILE_BYTES = 20L * 1024L * 1024L;

    private Textures() {
    }

    static Identifier fromFile(Path path, String kind) {
        if (path == null || !Files.isRegularFile(path)) {
            return null;
        }
        try {
            long size = Files.size(path);
            if (size <= 0L || size > MAX_FILE_BYTES) {
                return null;
            }
            long modified = Files.getLastModifiedTime(path).toMillis();
            String key = kind + ":" + path.toAbsolutePath() + ":" + modified + ":" + size;
            synchronized (CACHE) {
                if (CACHE.containsKey(key)) {
                    return CACHE.get(key);
                }
            }
            Identifier loaded = fromBytes(key, Files.readAllBytes(path));
            synchronized (CACHE) {
                CACHE.put(key, loaded);
            }
            return loaded;
        } catch (IOException | RuntimeException exception) {
            MusicBox.LOGGER.warn("Could not load image {}", path, exception);
            return null;
        }
    }

    static Identifier fromBytes(String cacheKey, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        synchronized (CACHE) {
            if (CACHE.containsKey(cacheKey)) {
                return CACHE.get(cacheKey);
            }
        }

        NativeImage image = null;
        try {
            image = decode(bytes);
            if (image.getWidth() <= 0 || image.getHeight() <= 0) {
                image.close();
                return null;
            }

            String hash = Integer.toUnsignedString((cacheKey + ":" + bytes.length).hashCode(), 16);
            Identifier id = Identifier.fromNamespaceAndPath(
                    MusicBox.MOD_ID.toLowerCase(Locale.ROOT),
                    "generated/" + hash
            );
            int width = image.getWidth();
            int height = image.getHeight();
            DynamicTexture texture = new DynamicTexture(() -> "MusicBox " + id, image);
            Minecraft.getInstance().getTextureManager().register(id, texture);
            synchronized (CACHE) {
                CACHE.put(cacheKey, id);
                SIZES.put(id, new int[]{width, height});
            }
            return id;
        } catch (IOException | RuntimeException exception) {
            if (image != null) {
                image.close();
            }
            MusicBox.LOGGER.warn("Could not decode image ({} bytes)", bytes.length, exception);
            return null;
        }
    }

    static int width(Identifier id) {
        int[] size = SIZES.get(id);
        return size == null ? 1 : size[0];
    }

    static int height(Identifier id) {
        int[] size = SIZES.get(id);
        return size == null ? 1 : size[1];
    }

    /**
     * NativeImage.read only decodes PNG in 1.21.11. Album art and user
     * wallpapers are commonly JPEG, so fall back to ImageIO and copy the
     * pixels into an RGBA NativeImage.
     */
    private static NativeImage decode(byte[] bytes) throws IOException {
        try {
            return NativeImage.read(bytes);
        } catch (IOException notPng) {
            BufferedImage buffered = ImageIO.read(new ByteArrayInputStream(bytes));
            if (buffered == null) {
                throw notPng;
            }
            int width = buffered.getWidth();
            int height = buffered.getHeight();
            if (width <= 0 || height <= 0) {
                throw new IOException("Empty image");
            }
            NativeImage image = new NativeImage(NativeImage.Format.RGBA, width, height, false);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    image.setPixel(x, y, buffered.getRGB(x, y));
                }
            }
            return image;
        }
    }
}
