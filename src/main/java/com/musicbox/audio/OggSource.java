package com.musicbox.audio;

import org.lwjgl.stb.STBVorbis;
import org.lwjgl.stb.STBVorbisInfo;
import org.lwjgl.system.MemoryUtil;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * OGG Vorbis via stb_vorbis, which ships with Minecraft/LWJGL. No extra native
 * library is required.
 */
public final class OggSource implements PcmSource {
    private static final int CHUNK_FRAMES = 4096;

    private final Path file;
    private final AudioFormat format;
    private final int channels;
    private final int sampleRate;
    private final long totalSamples;
    private final ByteBuffer fileBuffer;
    private final long handle;
    private final short[] pcm;

    private long positionSamples;
    private int pcmPosition;
    private int pcmLength;
    private boolean closed;

    public OggSource(Path file) throws IOException {
        this.file = file;
        long size = Files.size(file);
        if (size <= 0L || size > Integer.MAX_VALUE) {
            throw new IOException("OGG file is empty or too large: " + file.getFileName());
        }

        ByteBuffer buffer = MemoryUtil.memAlloc((int) size);
        long vorbis = 0L;
        IntBuffer error = MemoryUtil.memAllocInt(1);
        STBVorbisInfo info = STBVorbisInfo.malloc();
        try {
            buffer.put(Files.readAllBytes(file)).flip();
            vorbis = STBVorbis.stb_vorbis_open_memory(buffer, error, null);
            if (vorbis == 0L) {
                throw new IOException("stb_vorbis could not open " + file.getFileName() + " (error " + error.get(0) + ")");
            }
            STBVorbis.stb_vorbis_get_info(vorbis, info);
            channels = info.channels();
            sampleRate = info.sample_rate();
            if (channels <= 0 || sampleRate <= 0) {
                throw new IOException("Invalid OGG stream: " + file.getFileName());
            }
            long samples = STBVorbis.stb_vorbis_stream_length_in_samples(vorbis);
            totalSamples = samples > 0L ? samples : -1L;
        } catch (IOException | RuntimeException exception) {
            if (vorbis != 0L) {
                STBVorbis.stb_vorbis_close(vorbis);
            }
            MemoryUtil.memFree(buffer);
            throw exception instanceof IOException io ? io : new IOException("Cannot decode OGG: " + file.getFileName(), exception);
        } finally {
            info.free();
            MemoryUtil.memFree(error);
        }

        fileBuffer = buffer;
        handle = vorbis;
        pcm = new short[CHUNK_FRAMES * channels];
        format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, sampleRate, 16, channels, channels * 2, sampleRate, false);
    }

    @Override
    public AudioFormat format() {
        return format;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        if (pcmPosition >= pcmLength) {
            int frames = STBVorbis.stb_vorbis_get_samples_short_interleaved(handle, channels, pcm);
            if (frames <= 0) {
                return -1;
            }
            pcmLength = frames * channels;
            pcmPosition = 0;
        }

        int maxShorts = Math.min(length / 2, pcmLength - pcmPosition);
        if (maxShorts <= 0) {
            return 0;
        }

        int out = offset;
        for (int i = 0; i < maxShorts; i++) {
            short sample = pcm[pcmPosition + i];
            buffer[out++] = (byte) (sample & 0xFF);
            buffer[out++] = (byte) ((sample >>> 8) & 0xFF);
        }
        pcmPosition += maxShorts;
        positionSamples += maxShorts / channels;
        return maxShorts * 2;
    }

    @Override
    public void seek(long positionMs) throws IOException {
        long sample = Math.max(0L, positionMs * sampleRate / 1000L);
        if (totalSamples > 0L) {
            sample = Math.min(sample, totalSamples);
        }
        if (!STBVorbis.stb_vorbis_seek(handle, (int) sample)) {
            throw new IOException("Could not seek in " + file.getFileName());
        }
        positionSamples = sample;
        pcmPosition = 0;
        pcmLength = 0;
    }

    @Override
    public long positionMs() {
        return positionSamples * 1000L / Math.max(1, sampleRate);
    }

    @Override
    public long durationMs() {
        return totalSamples <= 0L ? -1L : totalSamples * 1000L / Math.max(1, sampleRate);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        STBVorbis.stb_vorbis_close(handle);
        MemoryUtil.memFree(fileBuffer);
    }
}
