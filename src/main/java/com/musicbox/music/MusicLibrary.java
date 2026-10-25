package com.musicbox.music;

import com.musicbox.MusicBox;
import com.musicbox.config.MusicBoxConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Scans the configured folder and resolves favorites.m3u / favorites.txt.
 * Everything here is local: no HTTP client, no account, no token.
 */
public final class MusicLibrary {
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("mp3", "ogg", "wav");
    private static final Comparator<Track> ORDER = Comparator
            .comparing(Track::artist, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(Track::title, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(track -> track.path().toString(), String.CASE_INSENSITIVE_ORDER);

    private List<Track> all = List.of();
    private List<Track> favorites = List.of();
    private Path root = MusicBoxConfig.musicDirectory();
    private String lastError;

    public void reload() {
        root = MusicBoxConfig.musicDirectory();
        lastError = null;
        List<Track> found = new ArrayList<>();
        try {
            Files.createDirectories(root);
            try (Stream<Path> stream = Files.walk(root, 8)) {
                stream.filter(Files::isRegularFile)
                        .filter(this::isSupported)
                        .map(Track::fromPath)
                        .forEach(found::add);
            }
        } catch (IOException exception) {
            lastError = exception.getMessage();
            MusicBox.LOGGER.warn("Could not scan music folder {}", root, exception);
        }

        found.sort(ORDER);
        all = List.copyOf(found);
        favorites = List.copyOf(readFavorites(all));
    }

    public List<Track> all() {
        return all;
    }

    public List<Track> favorites() {
        return favorites;
    }

    public Path root() {
        return root;
    }

    public String lastError() {
        return lastError;
    }

    private boolean isSupported(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return false;
        }
        return SUPPORTED_EXTENSIONS.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    private List<Track> readFavorites(List<Track> available) {
        Path playlist = firstExisting(root.resolve("favorites.m3u"), root.resolve("favorites.m3u8"), root.resolve("favorites.txt"));
        if (playlist == null) {
            return List.of();
        }

        Map<String, Track> byPath = new HashMap<>();
        Map<String, List<Track>> byName = new HashMap<>();
        for (Track track : available) {
            byPath.put(track.path().toString().toLowerCase(Locale.ROOT), track);
            byName.computeIfAbsent(track.fileName().toLowerCase(Locale.ROOT), key -> new ArrayList<>()).add(track);
        }

        List<Track> result = new ArrayList<>();
        Set<Path> seen = new HashSet<>();
        try {
            for (String rawLine : Files.readAllLines(playlist, StandardCharsets.UTF_8)) {
                String line = rawLine;
                if (!line.isEmpty() && line.charAt(0) == '\uFEFF') {
                    line = line.substring(1);
                }
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                Track resolved = resolveLine(line, byPath, byName);
                if (resolved != null && seen.add(resolved.path())) {
                    result.add(resolved);
                }
            }
        } catch (IOException exception) {
            lastError = exception.getMessage();
            MusicBox.LOGGER.warn("Could not read playlist {}", playlist, exception);
        }
        return result;
    }

    private Track resolveLine(String line, Map<String, Track> byPath, Map<String, List<Track>> byName) {
        try {
            Path candidate = Path.of(line);
            if (!candidate.isAbsolute()) {
                candidate = root.resolve(candidate);
            }
            candidate = candidate.toAbsolutePath().normalize();
            Track direct = byPath.get(candidate.toString().toLowerCase(Locale.ROOT));
            if (direct != null) {
                return direct;
            }

            String name = candidate.getFileName() == null ? "" : candidate.getFileName().toString().toLowerCase(Locale.ROOT);
            List<Track> matches = byName.get(name);
            if (matches != null && matches.size() == 1) {
                return matches.get(0);
            }
        } catch (Exception ignored) {
            // Fall through to a plain file-name lookup.
        }

        List<Track> byFileName = byName.get(line.toLowerCase(Locale.ROOT));
        return byFileName == null || byFileName.size() != 1 ? null : byFileName.get(0);
    }

    private Path firstExisting(Path... candidates) {
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }
}
