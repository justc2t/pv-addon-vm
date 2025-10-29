package org.justc2t.voiceMessages.audio;

import su.plo.voice.api.audio.codec.AudioDecoder;
import su.plo.voice.api.server.PlasmoVoiceServer;

import javax.sound.sampled.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Recording {
    private final String id;
    private final Path wavPath;
    private final AudioDecoder decoder;
    private final List<short[]> pcmFrames = new ArrayList<>();
    private boolean finished = false;
    private boolean playing = false;
    private int durationSeconds;

    private static final int SAMPLE_RATE = 48000;

    public Recording(Path wavPath, PlasmoVoiceServer server) {
        this.id = UUID.randomUUID().toString().substring(0, 8);
        this.wavPath = wavPath;
        this.decoder = server.createOpusDecoder(false);
    }

    public String getId() { return id; }
    public Path getWavPath() { return wavPath; }
    public boolean isFinished() { return finished; }
    public boolean isPlaying() { return playing; }
    public void setPlaying(boolean playing) { this.playing = playing; }

    public void addEncodedFrame(byte[] decrypted) {
        try {
            short[] pcm = decoder.decode(decrypted);
            pcmFrames.add(pcm);
        } catch (Exception ignored) {}
    }

    public void finalizeToWav() throws IOException {
        finished = true;
        if (pcmFrames.isEmpty()) return;

        List<Short> allSamples = new ArrayList<>();
        for (short[] frame : pcmFrames) {
            for (short s : frame) allSamples.add(s);
        }

        short[] normalized = normalize(allSamples);

        writeWav(normalized);

        durationSeconds = normalized.length / SAMPLE_RATE;

        try { decoder.close(); } catch (Exception ignored) {}
    }

    private short[] normalize(List<Short> samples) {
        short[] pcm = new short[samples.size()];
        short max = 1;
        for (short s : samples) if (Math.abs(s) > max) max = (short)Math.abs(s);

        float gain = (max < 2000) ? (2000f / max) : 1f;
        for (int i = 0; i < samples.size(); i++) {
            pcm[i] = (short)Math.max(Math.min(samples.get(i) * gain, Short.MAX_VALUE), Short.MIN_VALUE);
        }
        return pcm;
    }

    private void writeWav(short[] pcm) throws IOException {
        AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        for (short s : pcm) {
            baos.write(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(s).array());
        }

        byte[] rawData = baos.toByteArray();
        try (AudioInputStream ais = new AudioInputStream(
                new ByteArrayInputStream(rawData),
                format,
                pcm.length
        )) {
            AudioSystem.write(ais, AudioFileFormat.Type.WAVE, wavPath.toFile());
        }
    }

    public short[] loadPcmSamples() throws IOException {
        byte[] wav = java.nio.file.Files.readAllBytes(wavPath);
        int offset = 44;
        int samples = (wav.length - offset) / 2;
        short[] shorts = new short[samples];
        for (int i = 0; i < samples; i++) {
            int lo = wav[offset + i * 2] & 0xFF;
            int hi = wav[offset + i * 2 + 1] & 0xFF;
            shorts[i] = (short)((hi << 8) | lo);
        }
        return shorts;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }
}
