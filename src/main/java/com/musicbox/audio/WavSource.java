package com.musicbox.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.IOException;
import java.nio.file.Path;

/**
 * WAV via Java Sound. Non-16-bit WAVs are converted to signed 16-bit PCM.
 */
public final class WavSource implements PcmSource {
    private final Path file;
    private AudioInputStream stream;
    private AudioFormat format;
    private long frameSize;
    private long framesRead;
    private long totalFrames;

    public WavSource(Path file) throws IOException {
        this.file = file;
        openStream();
    }

    private void openStream() throws IOException {
        try {
            AudioInputStream raw = AudioSystem.getAudioInputStream(file.toFile());
            AudioFormat base = raw.getFormat();
            AudioFormat target = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    base.getSampleRate(),
                    16,
                    base.getChannels(),
                    base.getChannels() * 2,
                    base.getSampleRate(),
                    false
            );

            if (base.matches(target)) {
                stream = raw;
            } else {
                stream = AudioSystem.getAudioInputStream(target, raw);
            }
            format = stream.getFormat();
            frameSize = format.getFrameSize();
            totalFrames = stream.getFrameLength();
            framesRead = 0L;
        } catch (UnsupportedAudioFileException | IllegalArgumentException exception) {
            throw new IOException("Cannot decode WAV: " + file.getFileName(), exception);
        }
    }

    @Override
    public AudioFormat format() {
        return format;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        int read = stream.read(buffer, offset, length);
        if (read > 0) {
            framesRead += read / frameSize;
        }
        return read;
    }

    @Override
    public void seek(long positionMs) throws IOException {
        long frame = Math.max(0L, positionMs * (long) format.getFrameRate() / 1000L);
        if (totalFrames > 0) {
            frame = Math.min(frame, totalFrames);
        }
        closeStream();
        openStream();

        long remaining = frame * frameSize;
        while (remaining > 0L) {
            long skipped = stream.skip(remaining);
            if (skipped <= 0L) {
                if (stream.read() < 0) {
                    break;
                }
                skipped = 1L;
            }
            remaining -= skipped;
            framesRead += skipped / frameSize;
        }
        framesRead = frame;
    }

    @Override
    public long positionMs() {
        return (long) (framesRead * 1000.0D / Math.max(1.0D, format.getFrameRate()));
    }

    @Override
    public long durationMs() {
        if (totalFrames <= 0L) {
            return -1L;
        }
        return (long) (totalFrames * 1000.0D / Math.max(1.0D, format.getFrameRate()));
    }

    @Override
    public void close() {
        closeStream();
    }

    private void closeStream() {
        if (stream != null) {
            try {
                stream.close();
            } catch (IOException ignored) {
            }
            stream = null;
        }
    }
}
