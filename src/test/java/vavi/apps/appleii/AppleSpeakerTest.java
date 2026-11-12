/*
 * Copyright (c) 2026 by umjammer, All rights reserved.
 *
 * Released under the GPL
 */

package vavi.apps.appleii;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vavi.apps.appleii.AppleSpeaker.SPEAKER_SAMPLERATE;


/**
 * AppleSpeakerTest. Boots a rom that does nothing but play a tone, so the whole
 * speaker path can be checked without a sound card and without ears.
 *
 * @author umjammer
 */
class AppleSpeakerTest {

    /** an authentic apple ii runs at 1.02 MHz */
    static final int CPU_SPEED = 1020;
    static final int TONE = 1000;
    static final long DURATION = 3000;

    @Test
    @DisplayName("a rom toggling $c030 comes out as a tone of the right pitch")
    void testTone() throws Exception {
        byte[] pcm = new SpeakerHarness()
                .parameter("cpuRom", "tone.rom")
                .parameter("cpuSpeed", String.valueOf(CPU_SPEED))
                .parameter("speakerVolume", "5")
                .resource("tone.rom", ToneRom.create(CPU_SPEED, TONE))
                .run(DURATION);

        short[] samples = SpeakerHarness.samples(pcm);

        // the emulated clock decides how many samples exist, so a second of
        // emulation has to be a second of sound or the audio line starves
        double seconds = samples.length / (double) SPEAKER_SAMPLERATE;
        assertEquals(DURATION / 1000.0, seconds, DURATION / 1000.0 * 0.1,
                "produced %.2fs of sound in %.2fs".formatted(seconds, DURATION / 1000.0));

        // skip the boot, measure the tone over the last second
        int crossings = 0;
        int peak = 0;
        for (int i = samples.length - SPEAKER_SAMPLERATE + 1; i < samples.length; i++) {
            if ((samples[i] < 0) != (samples[i - 1] < 0)) {
                crossings++;
            }
            peak = Math.max(peak, Math.abs(samples[i]));
        }

        double expected = ToneRom.actualFrequency(CPU_SPEED, TONE);
        assertEquals(expected, crossings / 2.0, expected * 0.02, "pitch");
        assertTrue(peak > 1 << 12, "the tone is audible, peak: " + peak);
    }

    @Test
    @DisplayName("an untouched speaker settles to silence instead of a dc offset")
    void testSilence() throws Exception {
        byte[] pcm = new SpeakerHarness()
                .parameter("cpuRom", "silent.rom")
                .parameter("cpuSpeed", String.valueOf(CPU_SPEED))
                .resource("silent.rom", silentRom())
                .run(2000);

        short[] samples = SpeakerHarness.samples(pcm);
        assertTrue(samples.length > SPEAKER_SAMPLERATE, "got " + samples.length + " samples");

        // a standing offset is what turns every underrun and every stop into a pop
        int peak = 0;
        for (int i = samples.length / 2; i < samples.length; i++) {
            peak = Math.max(peak, Math.abs(samples[i]));
        }
        assertEquals(0, peak, "residual level");
    }

    /** a rom that just spins, never touching the speaker */
    static byte[] silentRom() {
        byte[] rom = new byte[0x8000];
        rom[0] = 0x4c; // jmp $d000
        rom[1] = 0x00;
        rom[2] = (byte) 0xd0;
        rom[0xe000 - 0xd000] = 0x4c;
        rom[0xfffc - 0xd000] = 0x00;
        rom[0xfffd - 0xd000] = (byte) 0xd0;
        return rom;
    }
}
