package com.musicbox.client;

import com.musicbox.MusicBox;
import com.musicbox.music.Track;
import net.minecraft.resources.Identifier;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Finds album art in this order:
 * 1. a sidecar image next to the track (base name, cover, folder, front),
 * 2. an ID3v2 APIC frame in an MP3,
 * 3. a Vorbis METADATA_BLOCK_PICTURE comment in an OGG.
 */
final class Artwork {
    private static final long MAX_ART_BYTES = 16L * 1024L * 1024L;
    private static final Map<String, Identifier> CACHE = new HashMap<>();

    private Artwork() {
    }

    static Identifier get(Track track) {
        if (track == null) {
            return null;
        }
        Path path = track.path();
        try {
            long size = Files.size(path);
            long modified = Files.getLastModifiedTime(path).toMillis();
            String key = path.toAbsolutePath() + ":" + modified + ":" + size;
            synchronized (CACHE) {
                if (CACHE.containsKey(key)) {
                    return CACHE.get(key);
                }
            }

            byte[] bytes = findArtwork(path);
            Identifier id = bytes == null ? null : Textures.fromBytes("album:" + key, bytes);
            synchronized (CACHE) {
                CACHE.put(key, id);
            }
            return id;
        } catch (IOException | RuntimeException exception) {
            MusicBox.LOGGER.debug("Could not read artwork for {}", path, exception);
            return null;
        }
    }

    private static byte[] findArtwork(Path audio) throws IOException {
        byte[] sidecar = readSidecar(audio);
        if (sidecar != null) {
            return sidecar;
        }

        String name = audio.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".mp3")) {
            return readMp3Picture(audio);
        }
        if (name.endsWith(".ogg")) {
            return readOggPicture(audio);
        }
        return null;
    }

    private static byte[] readSidecar(Path audio) throws IOException {
        Path parent = audio.getParent();
        if (parent == null) {
            return null;
        }
        String fileName = audio.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        List<String> candidates = List.of(
                base + ".png", base + ".jpg", base + ".jpeg",
                "cover.png", "cover.jpg", "cover.jpeg",
                "folder.png", "folder.jpg", "folder.jpeg",
                "front.png", "front.jpg", "front.jpeg"
        );

        for (String candidate : candidates) {
            Path path = parent.resolve(candidate);
            if (Files.isRegularFile(path)) {
                return readImage(path);
            }
        }

        // Linux is case sensitive; try a case-insensitive match as a fallback.
        Map<String, Path> lowerCase = new HashMap<>();
        try (Stream<Path> stream = Files.list(parent)) {
            stream.filter(Files::isRegularFile)
                    .forEach(path -> lowerCase.putIfAbsent(path.getFileName().toString().toLowerCase(Locale.ROOT), path));
        }
        for (String candidate : candidates) {
            Path path = lowerCase.get(candidate.toLowerCase(Locale.ROOT));
            if (path != null) {
                return readImage(path);
            }
        }
        return null;
    }

    private static byte[] readImage(Path path) throws IOException {
        long size = Files.size(path);
        if (size <= 0L || size > MAX_ART_BYTES) {
            return null;
        }
        return Files.readAllBytes(path);
    }

    private static byte[] readMp3Picture(Path file) throws IOException {
        try (InputStream input = new BufferedInputStream(Files.newInputStream(file), 1 << 16)) {
            byte[] header = input.readNBytes(10);
            if (header.length < 10
                    || header[0] != 'I' || header[1] != 'D' || header[2] != '3') {
                return null;
            }
            int version = header[3] & 0xFF;
            if (version != 3 && version != 4) {
                return null;
            }
            int flags = header[5] & 0xFF;
            int tagSize = synchsafe(header, 6);
            if (tagSize <= 0 || tagSize > 32 * 1024 * 1024) {
                return null;
            }
            byte[] tag = input.readNBytes(tagSize);
            int position = 0;
            if ((flags & 0x40) != 0) {
                if (version == 4) {
                    position = synchsafe(tag, 0);
                } else {
                    position = bigEndianInt(tag, 0) + 4;
                }
            }

            while (position + 10 <= tag.length) {
                String id = new String(tag, position, 4, StandardCharsets.ISO_8859_1);
                if (id.charAt(0) == '\0') {
                    break;
                }
                int frameSize = version == 4
                        ? synchsafe(tag, position + 4)
                        : bigEndianInt(tag, position + 4);
                if (frameSize <= 0 || position + 10 + frameSize > tag.length) {
                    break;
                }
                if ("APIC".equals(id)) {
                    return parseApic(tag, position + 10, frameSize);
                }
                position += 10 + frameSize;
            }
        }
        return null;
    }

    private static byte[] parseApic(byte[] tag, int start, int length) {
        int end = Math.min(tag.length, start + length);
        int position = start;
        if (position >= end) {
            return null;
        }
        int encoding = tag[position++] & 0xFF;

        while (position < end && tag[position] != 0) {
            position++;
        }
        if (position >= end) {
            return null;
        }
        position++; // MIME terminator
        position++; // picture type
        if (position >= end) {
            return null;
        }

        if (encoding == 1 || encoding == 2) {
            while (position + 1 < end && !(tag[position] == 0 && tag[position + 1] == 0)) {
                position += 2;
            }
            position += 2;
        } else {
            while (position < end && tag[position] != 0) {
                position++;
            }
            position++;
        }

        if (position >= end) {
            return null;
        }
        byte[] image = Arrays.copyOfRange(tag, position, end);
        return image.length == 0 ? null : image;
    }

    private static byte[] readOggPicture(Path file) throws IOException {
        long size = Files.size(file);
        if (size <= 0L || size > 48L * 1024L * 1024L) {
            return null;
        }
        byte[] bytes = Files.readAllBytes(file);
        String latin = new String(bytes, StandardCharsets.ISO_8859_1);
        String marker = "METADATA_BLOCK_PICTURE=";
        int markerIndex = latin.indexOf(marker);
        if (markerIndex < 0) {
            return null;
        }
        int position = markerIndex + marker.length();
        int end = position;
        while (end < latin.length() && isBase64(latin.charAt(end))) {
            end++;
        }
        if (end <= position) {
            return null;
        }

        try {
            byte[] block = Base64.getDecoder().decode(latin.substring(position, end));
            return parseFlacPicture(block);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static byte[] parseFlacPicture(byte[] block) {
        int position = 0;
        if (block.length < 32) {
            return null;
        }
        position += 4; // picture type
        int mimeLength = bigEndianInt(block, position);
        position += 4;
        if (mimeLength < 0 || position + mimeLength + 4 > block.length) {
            return null;
        }
        position += mimeLength;
        int descriptionLength = bigEndianInt(block, position);
        position += 4;
        if (descriptionLength < 0 || position + descriptionLength + 20 > block.length) {
            return null;
        }
        position += descriptionLength;
        position += 16; // width, height, depth, colors
        int dataLength = bigEndianInt(block, position);
        position += 4;
        if (dataLength <= 0 || position + dataLength > block.length) {
            return null;
        }
        return Arrays.copyOfRange(block, position, position + dataLength);
    }

    private static boolean isBase64(char character) {
        return (character >= 'A' && character <= 'Z')
                || (character >= 'a' && character <= 'z')
                || (character >= '0' && character <= '9')
                || character == '+' || character == '/' || character == '=';
    }

    private static int synchsafe(byte[] bytes, int offset) {
        if (offset < 0 || offset + 4 > bytes.length) {
            return -1;
        }
        return ((bytes[offset] & 0x7F) << 21)
                | ((bytes[offset + 1] & 0x7F) << 14)
                | ((bytes[offset + 2] & 0x7F) << 7)
                | (bytes[offset + 3] & 0x7F);
    }

    private static int bigEndianInt(byte[] bytes, int offset) {
        if (offset < 0 || offset + 4 > bytes.length) {
            return -1;
        }
        return ((bytes[offset] & 0xFF) << 24)
                | ((bytes[offset + 1] & 0xFF) << 16)
                | ((bytes[offset + 2] & 0xFF) << 8)
                | (bytes[offset + 3] & 0xFF);
    }
}
