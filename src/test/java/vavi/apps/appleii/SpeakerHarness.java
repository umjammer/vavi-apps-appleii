/*
 * Copyright (c) 2026 by umjammer, All rights reserved.
 *
 * Released under the GPL
 */

package vavi.apps.appleii;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.zip.GZIPInputStream;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;

import static vavi.apps.appleii.AppleSpeaker.SPEAKER_BIGENDIAN;
import static vavi.apps.appleii.AppleSpeaker.SPEAKER_BITS;
import static vavi.apps.appleii.AppleSpeaker.SPEAKER_CHANNELS;
import static vavi.apps.appleii.AppleSpeaker.SPEAKER_SAMPLERATE;
import static vavi.apps.appleii.AppleSpeaker.SPEAKER_SAMPLESIZE;
import static vavi.apps.appleii.AppleSpeaker.SPEAKER_SIGNED;


/**
 * Runs the emulator with no window and keeps everything the speaker emits, so
 * sound can be checked without an audio device and without ears.
 *
 * @author umjammer
 */
public class SpeakerHarness implements AppleIIGo.View, AppleIIGo.Dao {

    /** where the interactive app keeps its rom and disks */
    public static final Path CONFIG = Path.of(System.getProperty("user.home"), ".config/appleiigo");

    public static final AudioFormat FORMAT = new AudioFormat(
            SPEAKER_SAMPLERATE, SPEAKER_BITS, SPEAKER_CHANNELS, SPEAKER_SIGNED, SPEAKER_BIGENDIAN);

    private final Properties props = new Properties();
    /** resources served from memory, anything else is read from {@link #CONFIG} */
    private final Map<String, byte[]> resources = new HashMap<>();
    private final ByteArrayOutputStream captured = new ByteArrayOutputStream();
    private final int[] imageBuffer = new int[AppleDisplay.DISPLAY_SIZE_X * AppleDisplay.DISPLAY_SIZE_Y];

    private final AppleIIGo game = new AppleIIGo();

    /** null unless a real line was asked for */
    private SourceDataLine line;
    private int writes, underruns;

    public SpeakerHarness() {
        game.setView(this);
        game.setDao(this);
    }

    public SpeakerHarness parameter(String key, String value) {
        props.setProperty(key, value);
        return this;
    }

    public SpeakerHarness resource(String name, byte[] data) {
        resources.put(name, data);
        return this;
    }

    /** also plays through the default mixer, and counts how often it ran dry */
    public SpeakerHarness live() {
        props.setProperty("speakerLive", "true");
        return this;
    }

    /** keys tapped in a loop while running, to get past title screens */
    private int[] keys = {};
    private long keyInterval = 2000;

    public SpeakerHarness keys(long interval, int... keys) {
        this.keyInterval = interval;
        this.keys = keys;
        return this;
    }

    /** boots and runs for the given wall clock time */
    public byte[] run(long millis) throws InterruptedException {
        game.init();
        game.start();

        long end = System.currentTimeMillis() + millis;
        for (int i = 0; System.currentTimeMillis() < end; i++) {
            Thread.sleep(Math.min(keys.length == 0 ? millis : keyInterval,
                    Math.max(1, end - System.currentTimeMillis())));
            if (keys.length > 0) {
                game.setKeyLatch(keys[i % keys.length]);
            }
        }

        game.pause();
        if (line != null) {
            line.close();
        }
        return captured.toByteArray();
    }

    public int getWrites() {
        return writes;
    }

    public int getUnderruns() {
        return underruns;
    }

    public static void write(byte[] pcm, Path out) throws IOException {
        AudioSystem.write(
                new AudioInputStream(new ByteArrayInputStream(pcm), FORMAT, pcm.length / FORMAT.getFrameSize()),
                AudioFileFormat.Type.WAVE,
                out.toFile());
    }

    /** @return the samples of the captured stream */
    public static short[] samples(byte[] pcm) {
        short[] samples = new short[pcm.length / SPEAKER_SAMPLESIZE];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = (short) ((pcm[i * 2] & 0xff) | (pcm[i * 2 + 1] << 8));
        }
        return samples;
    }

    // -- Dao --

    @Override
    public String getParameter(String parameter) {
        return props.getProperty(parameter);
    }

    private InputStream is;

    @Override
    public void openInputStream(String resource) {
        try {
            byte[] data = resources.get(resource);
            is = data != null ? new ByteArrayInputStream(data) : Files.newInputStream(CONFIG.resolve(resource));
            if (resource.toLowerCase().endsWith(".gz")) {
                is = new GZIPInputStream(is);
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void read(byte[] bytes, int offset, int length) {
        try {
            int l = 0;
            while (l < length) {
                int r = is.read(bytes, offset + l, length - l);
                if (r < 0) {
                    break;
                }
                l += r;
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void closeInputStream() {
        try {
            is.close();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    // -- View --

    @Override public int[] createImageBuffer() { return imageBuffer; }
    @Override public void repaint() {}
    @Override public void getCharSet(int[] buffer, int w, int h, int s) {}
    @Override public void setDisplayScaledSizeX(int w) {}
    @Override public void setDisplayScaledSizeY(int h) {}
    @Override public void debug(Throwable t) { t.printStackTrace(); }
    @Override public void debug(String s) { System.err.println(s); }

    @Override
    public int initAudio() {
        int bufferSize = SPEAKER_SAMPLERATE * SPEAKER_SAMPLESIZE
                * Integer.parseInt(props.getProperty("speakerLatency", "120")) / 1000;
        if (!"true".equals(props.getProperty("speakerLive"))) {
            return bufferSize;
        }
        try {
            line = (SourceDataLine) AudioSystem.getLine(new DataLine.Info(SourceDataLine.class, FORMAT));
            line.open(FORMAT, bufferSize);
            bufferSize = line.getBufferSize();
            line.write(new byte[bufferSize / 2], 0, bufferSize / 2);
            line.start();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return bufferSize;
    }

    @Override public boolean isAudioAvailable() { return true; }
    @Override public void closeAudio() {}

    @Override
    public void audioWrite(byte[] buffer, int offset, int length) {
        captured.write(buffer, offset, length);
        if (line != null) {
            writes++;
            if (line.available() == line.getBufferSize()) {
                underruns++;
            }
            line.write(buffer, offset, length);
        }
    }
}
