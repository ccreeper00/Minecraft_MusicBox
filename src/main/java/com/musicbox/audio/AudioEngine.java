package com.musicbox.audio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.nio.file.Path;

/**
 * Plays one {@link PcmSource} at a time on a dedicated thread using Java Sound.
 * This deliberately does not use Minecraft's OpenAL sound engine, so playback
 * continues while the game is paused or the GUI is open.
 */
public final class AudioEngine {
    private static final Logger LOGGER = LoggerFactory.getLogger("MusicBox/Audio");
    private static final int BUFFER_BYTES = 8192;

    public enum State {
        STOPPED,
        PLAYING,
        PAUSED
    }

    private final Object sourceLock = new Object();

    private volatile State state = State.STOPPED;
    private volatile Path currentPath;
    private volatile long positionMs;
    private volatile long durationMs = -1L;
    private volatile float volume = 0.7F;
    private volatile boolean stopRequested;
    private volatile boolean paused;
    private volatile Thread thread;
    private volatile SourceDataLine line;
    private volatile PcmSource source;
    private volatile Runnable onTrackEnd = () -> {
    };

    public void setOnTrackEnd(Runnable onTrackEnd) {
        this.onTrackEnd = onTrackEnd == null ? () -> {
        } : onTrackEnd;
    }

    public synchronized void play(Path path) {
        stop();
        stopRequested = false;
        paused = false;
        currentPath = path;
        positionMs = 0L;
        durationMs = -1L;
        state = State.PLAYING;

        Thread audioThread = new Thread(() -> run(path), "MusicBox-Audio");
        audioThread.setDaemon(true);
        thread = audioThread;
        audioThread.start();
    }

    private void run(Path path) {
        PcmSource localSource = null;
        SourceDataLine localLine = null;
        boolean naturalEnd = false;
        try {
            localSource = PcmSource.open(path);
            synchronized (sourceLock) {
                source = localSource;
            }
            durationMs = localSource.durationMs();

            AudioFormat format = localSource.format();
            localLine = AudioSystem.getSourceDataLine(format);
            localLine.open(format, 32 * 1024);
            localLine.start();
            line = localLine;

            byte[] buffer = new byte[BUFFER_BYTES];
            while (!stopRequested) {
                if (paused) {
                    Thread.sleep(20L);
                    continue;
                }

                int read;
                synchronized (sourceLock) {
                    read = localSource.read(buffer, 0, buffer.length);
                }
                if (read < 0) {
                    naturalEnd = true;
                    break;
                }
                if (read == 0) {
                    continue;
                }

                applyVolume(buffer, read);
                localLine.write(buffer, 0, read);
                positionMs = localSource.positionMs();
            }

            if (naturalEnd && !stopRequested) {
                localLine.drain();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (Exception exception) {
            if (!stopRequested) {
                LOGGER.warn("Playback failed for {}", path, exception);
            }
        } finally {
            if (localLine != null) {
                try {
                    localLine.stop();
                    localLine.flush();
                    localLine.close();
                } catch (Exception ignored) {
                }
            }
            if (localSource != null) {
                synchronized (sourceLock) {
                    localSource.close();
                    if (source == localSource) {
                        source = null;
                    }
                }
            }
            line = null;
            if (thread == Thread.currentThread()) {
                thread = null;
            }

            if (!paused) {
                state = State.STOPPED;
            }
            if (naturalEnd && !stopRequested) {
                positionMs = durationMs > 0L ? durationMs : positionMs;
                Runnable callback = onTrackEnd;
                Thread next = new Thread(callback, "MusicBox-TrackEnd");
                next.setDaemon(true);
                next.start();
            }
        }
    }

    public synchronized void stop() {
        stopRequested = true;
        paused = false;
        state = State.STOPPED;

        SourceDataLine currentLine = line;
        if (currentLine != null) {
            try {
                currentLine.stop();
                currentLine.flush();
                currentLine.close();
            } catch (Exception ignored) {
            }
        }

        synchronized (sourceLock) {
            if (source != null) {
                source.close();
                source = null;
            }
        }

        Thread currentThread = thread;
        if (currentThread != null && currentThread != Thread.currentThread()) {
            try {
                currentThread.join(1200L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
        thread = null;
        positionMs = 0L;
        durationMs = -1L;
    }

    public void togglePause() {
        State current = state;
        if (current == State.PLAYING) {
            paused = true;
            state = State.PAUSED;
            SourceDataLine currentLine = line;
            if (currentLine != null) {
                currentLine.flush();
            }
        } else if (current == State.PAUSED) {
            paused = false;
            state = State.PLAYING;
            SourceDataLine currentLine = line;
            if (currentLine != null) {
                currentLine.start();
            }
        }
    }

    public void seek(long positionMs) {
        PcmSource currentSource = source;
        if (currentSource == null) {
            return;
        }
        try {
            synchronized (sourceLock) {
                currentSource.seek(Math.max(0L, positionMs));
            }
            SourceDataLine currentLine = line;
            if (currentLine != null) {
                currentLine.flush();
            }
            this.positionMs = Math.max(0L, positionMs);
        } catch (Exception exception) {
            LOGGER.warn("Seek failed at {} ms", positionMs, exception);
        }
    }

    public void setVolume(float volume) {
        this.volume = Math.max(0.0F, Math.min(1.0F, volume));
    }

    public float volume() {
        return volume;
    }

    public State state() {
        return state;
    }

    public boolean isPlaying() {
        return state == State.PLAYING;
    }

    public boolean isPaused() {
        return state == State.PAUSED;
    }

    public Path currentPath() {
        return currentPath;
    }

    public long positionMs() {
        return positionMs;
    }

    public long durationMs() {
        return durationMs;
    }

    private void applyVolume(byte[] buffer, int length) {
        float gain = volume;
        if (gain >= 0.999F) {
            return;
        }
        for (int i = 0; i + 1 < length; i += 2) {
            short sample = (short) ((buffer[i] & 0xFF) | (buffer[i + 1] << 8));
            int scaled = (int) (sample * gain);
            if (scaled > Short.MAX_VALUE) {
                scaled = Short.MAX_VALUE;
            } else if (scaled < Short.MIN_VALUE) {
                scaled = Short.MIN_VALUE;
            }
            buffer[i] = (byte) (scaled & 0xFF);
            buffer[i + 1] = (byte) ((scaled >>> 8) & 0xFF);
        }
    }
}
