package com.musicbox.audio;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.BitstreamException;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.DecoderException;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;

import javax.sound.sampled.AudioFormat;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * MP3 via JLayer (LGPL-2.1). Frames are decoded on demand and converted to
 * little-endian 16-bit PCM, which is exactly what Java Sound wants.
 */
public final class Mp3Source implements PcmSource {
    private final Path file;
    private final long fileSize;

    private Bitstream bitstream;
    private Decoder decoder;
    private AudioFormat format;
    private int channels;
    private int sampleRate;
    private long durationMs = -1L;
    private long positionSamples;

    private byte[] pending = new byte[0];
    private int pendingOffset;
    private boolean closed;

    public Mp3Source(Path file) throws IOException {
        this.file = file;
        this.fileSize = Files.size(file);
        this.durationMs = scanDuration();
        openAt(0L);
    }

    /**
     * JLayer's file-size estimate is very wrong for VBR files. Walking the
     * frame headers once gives an accurate duration without decoding audio.
     */
    private long scanDuration() {
        Bitstream scanner = null;
        try {
            scanner = new Bitstream(new BufferedInputStream(Files.newInputStream(file), 1 << 16));
            double totalMs = 0.0D;
            Header header;
            while ((header = scanner.readFrame()) != null) {
                totalMs += header.ms_per_frame();
                scanner.closeFrame();
            }
            return totalMs > 0.0D ? (long) totalMs : -1L;
        } catch (Exception exception) {
            return -1L;
        } finally {
            if (scanner != null) {
                try {
                    scanner.close();
                } catch (BitstreamException ignored) {
                }
            }
        }
    }

    private void openAt(long targetMs) throws IOException {
        closeStream();
        closed = false;
        try {
            InputStream input = new BufferedInputStream(Files.newInputStream(file), 1 << 16);
            bitstream = new Bitstream(input);
            decoder = new Decoder();
            positionSamples = 0L;
            pending = new byte[0];
            pendingOffset = 0;

            Header header;
            while ((header = bitstream.readFrame()) != null) {
                SampleBuffer sample = (SampleBuffer) decoder.decodeFrame(header, bitstream);
                bitstream.closeFrame();

                if (channels == 0) {
                    channels = sample.getChannelCount();
                    sampleRate = sample.getSampleFrequency();
                    if (channels <= 0 || sampleRate <= 0) {
                        throw new IOException("Invalid MP3 stream: " + file.getFileName());
                    }
                    if (durationMs <= 0L) {
                        durationMs = estimateDuration(header);
                    }
                    format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, sampleRate, 16, channels, channels * 2, sampleRate, false);
                }

                long frameSamples = sample.getBufferLength() / Math.max(1, sample.getChannelCount());
                long frameStartMs = positionSamples * 1000L / Math.max(1, sampleRate);
                if (frameStartMs >= targetMs) {
                    setPending(sample);
                    return;
                }
                positionSamples += frameSamples;
            }
            throw new IOException("MP3 has no decodable frames: " + file.getFileName());
        } catch (IOException exception) {
            closeStream();
            throw exception;
        } catch (Exception exception) {
            closeStream();
            throw new IOException("Cannot decode MP3: " + file.getFileName(), exception);
        }
    }

    private long estimateDuration(Header header) {
        float total = header.total_ms((int) Math.min(Integer.MAX_VALUE, fileSize));
        if (total > 0.0F && Float.isFinite(total)) {
            return (long) total;
        }
        int bitrate = header.bitrate();
        if (bitrate > 0) {
            return fileSize * 8L / bitrate;
        }
        return -1L;
    }

    private void setPending(SampleBuffer sample) {
        short[] samples = sample.getBuffer();
        int count = sample.getBufferLength();
        byte[] bytes = new byte[count * 2];
        int out = 0;
        for (int i = 0; i < count; i++) {
            short value = samples[i];
            bytes[out++] = (byte) (value & 0xFF);
            bytes[out++] = (byte) ((value >>> 8) & 0xFF);
        }
        pending = bytes;
        pendingOffset = 0;
    }

    @Override
    public AudioFormat format() {
        return format;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        if (closed) {
            return -1;
        }
        if (pendingOffset >= pending.length) {
            if (!decodeNextFrame()) {
                return -1;
            }
        }

        int count = Math.min(length, pending.length - pendingOffset);
        count &= ~1;
        if (count <= 0) {
            return 0;
        }
        System.arraycopy(pending, pendingOffset, buffer, offset, count);
        pendingOffset += count;
        positionSamples += count / (2L * Math.max(1, channels));
        return count;
    }

    private boolean decodeNextFrame() throws IOException {
        try {
            Header header = bitstream.readFrame();
            if (header == null) {
                return false;
            }
            SampleBuffer sample = (SampleBuffer) decoder.decodeFrame(header, bitstream);
            bitstream.closeFrame();
            setPending(sample);
            return true;
        } catch (BitstreamException | DecoderException | RuntimeException exception) {
            throw new IOException("Error decoding MP3: " + file.getFileName(), exception);
        }
    }

    @Override
    public void seek(long positionMs) throws IOException {
        long target = Math.max(0L, positionMs);
        if (durationMs > 0L) {
            target = Math.min(target, Math.max(0L, durationMs - 1L));
        }
        // Seeking re-opens the stream and fast-decodes frames up to the target.
        // This is simple and works for both CBR and VBR files.
        openAt(target);
    }

    @Override
    public long positionMs() {
        return positionSamples * 1000L / Math.max(1, sampleRate);
    }

    @Override
    public long durationMs() {
        return durationMs;
    }

    @Override
    public void close() {
        closed = true;
        closeStream();
    }

    private void closeStream() {
        if (bitstream != null) {
            try {
                bitstream.close();
            } catch (BitstreamException ignored) {
            }
            bitstream = null;
        }
    }
}
