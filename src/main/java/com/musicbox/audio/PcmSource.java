package com.musicbox.audio;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

/**
 * A streaming 16-bit PCM source. The playback thread only sees this interface,
 * which keeps WAV / OGG / MP3 handling independent from each other.
 */
public interface PcmSource extends AutoCloseable {
    AudioFormat format();

    /**
     * @return number of bytes read, or -1 at end of stream
     */
    int read(byte[] buffer, int offset, int length) throws IOException;

    void seek(long positionMs) throws IOException;

    long positionMs();

    /**
     * @return duration in milliseconds, or -1 when it cannot be determined
     */
    long durationMs();

    @Override
    void close();

    static PcmSource open(Path path) throws IOException {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String extension = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
        return switch (extension) {
            case "wav" -> new WavSource(path);
            case "ogg" -> new OggSource(path);
            case "mp3" -> new Mp3Source(path);
            default -> throw new IOException("Unsupported audio format: " + name);
        };
    }
}
