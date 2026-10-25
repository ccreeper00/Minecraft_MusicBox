package com.musicbox.music;

import com.musicbox.audio.AudioEngine;
import com.musicbox.config.MusicBoxConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.nio.file.Files;
import java.util.List;

/**
 * Owns the playlist and the single audio engine. All methods are called from
 * the client thread, except the end-of-track callback (which only schedules the
 * next track).
 */
public final class MusicPlayer {
    private static final MusicPlayer INSTANCE = new MusicPlayer();

    private final MusicLibrary library = new MusicLibrary();
    private final AudioEngine engine = new AudioEngine();

    private List<Track> queue = List.of();
    private int queueIndex = -1;
    private Track current;
    private boolean loaded;

    private MusicPlayer() {
        engine.setOnTrackEnd(this::next);
        engine.setVolume((float) MusicBoxConfig.get().volume);
    }

    public static MusicPlayer get() {
        return INSTANCE;
    }

    public synchronized void ensureLoaded() {
        if (!loaded) {
            reload();
        }
    }

    public synchronized void reload() {
        library.reload();
        loaded = true;
        if (current != null && !Files.isRegularFile(current.path())) {
            engine.stop();
            current = null;
            queue = List.of();
            queueIndex = -1;
        }
    }

    public synchronized void play(List<Track> tracks, int index) {
        if (!inWorld() || tracks == null || tracks.isEmpty()) {
            return;
        }
        List<Track> copy = List.copyOf(tracks);
        int selected = Math.floorMod(index, copy.size());
        queue = copy;
        queueIndex = selected;
        current = copy.get(selected);
        engine.setVolume((float) MusicBoxConfig.get().volume);
        engine.play(current.path());
        announce(current);
    }

    public synchronized void playTrack(Track track) {
        int index = queue.indexOf(track);
        if (index >= 0) {
            play(queue, index);
        } else {
            play(List.of(track), 0);
        }
    }

    public synchronized void toggle() {
        if (!inWorld()) {
            return;
        }
        if (current == null) {
            List<Track> tracks = library.all();
            if (!tracks.isEmpty()) {
                play(tracks, 0);
            }
            return;
        }
        if (engine.state() == AudioEngine.State.STOPPED) {
            engine.play(current.path());
        } else {
            engine.togglePause();
        }
    }

    public synchronized void next() {
        if (!inWorld() || queue.isEmpty()) {
            return;
        }
        play(queue, (queueIndex + 1) % queue.size());
    }

    public synchronized void previous() {
        if (!inWorld() || queue.isEmpty()) {
            return;
        }
        // Always go to the previous track. The old 3-second "restart current
        // track" shortcut made Previous look like it replayed the current song.
        play(queue, Math.floorMod(queueIndex - 1, queue.size()));
    }

    public synchronized void stop() {
        engine.stop();
    }

    /**
     * Called when the client leaves a world (or is on the title screen).
     * Java Sound keeps playing independently of Minecraft, so it must be
     * stopped explicitly and the stale queue/current track cleared.
     */
    public synchronized void stopForWorldExit() {
        engine.stop();
        current = null;
        queue = List.of();
        queueIndex = -1;
    }

    public void seekFraction(double fraction) {
        long duration = engine.durationMs();
        if (duration > 0L) {
            engine.seek((long) (duration * Math.max(0.0D, Math.min(1.0D, fraction))));
        }
    }

    public void setVolume(float volume) {
        engine.setVolume(volume);
        MusicBoxConfig.get().volume = engine.volume();
    }

    public void adjustVolume(float delta) {
        setVolume(engine.volume() + delta);
        MusicBoxConfig.save();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(
                    Component.translatable("gui.musicbox.volume", Math.round(engine.volume() * 100.0F)),
                    true
            );
        }
    }

    public MusicLibrary library() {
        return library;
    }

    public Track current() {
        return current;
    }

    public boolean hasCurrent() {
        return current != null;
    }

    public boolean isPlaying() {
        return engine.isPlaying();
    }

    public boolean isPaused() {
        return engine.isPaused();
    }

    public long positionMs() {
        return engine.positionMs();
    }

    public long durationMs() {
        return engine.durationMs();
    }

    public float volume() {
        return engine.volume();
    }

    private static boolean inWorld() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null && minecraft.level != null;
    }

    private void announce(Track track) {
        if (!MusicBoxConfig.get().showNowPlaying) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.literal("♪ " + track.displayName()), true);
        }
    }
}
