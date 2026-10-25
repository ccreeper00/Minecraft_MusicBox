package com.musicbox.music;

import java.nio.file.Path;
import java.util.Locale;

/**
 * One playable local file. Metadata is derived from the file name, so no tag
 * parser is required and no network access is ever performed.
 */
public record Track(Path path, String title, String artist) {
    public static Track fromPath(Path path) {
        String fileName = path.getFileName().toString();
        String base = fileName;
        int dot = base.lastIndexOf('.');
        if (dot > 0) {
            base = base.substring(0, dot);
        }
        base = base.trim();

        int separator = base.indexOf(" - ");
        String artist = "";
        String title = base;
        if (separator > 0) {
            artist = base.substring(0, separator).trim();
            title = base.substring(separator + 3).trim();
        }
        if (title.isBlank()) {
            title = fileName;
        }
        return new Track(path.toAbsolutePath().normalize(), title, artist);
    }

    public String displayName() {
        return artist.isBlank() ? title : artist + " - " + title;
    }

    public String fileName() {
        return path.getFileName().toString();
    }

    public String searchText() {
        return (displayName() + " " + fileName()).toLowerCase(Locale.ROOT);
    }
}
