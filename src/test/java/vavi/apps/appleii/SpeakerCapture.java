/*
 * Copyright (c) 2026 by umjammer, All rights reserved.
 *
 * Released under the GPL
 */

package vavi.apps.appleii;

import java.nio.file.Path;
import java.util.Properties;

import static vavi.apps.appleii.AppleSpeaker.SPEAKER_SAMPLERATE;
import static vavi.apps.appleii.AppleSpeaker.SPEAKER_SAMPLESIZE;


/**
 * Boots a real disk out of {@link SpeakerHarness#CONFIG} and dumps whatever the
 * speaker plays into a wav file, to tell which software actually uses sound.
 *
 * <pre>
 *  SpeakerCapture &lt;out.wav&gt; &lt;seconds&gt; [diskDrive1] [--live]
 * </pre>
 *
 * @author umjammer
 */
public class SpeakerCapture {

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args[0]);
        long millis = Long.parseLong(args[1]) * 1000;

        SpeakerHarness harness = new SpeakerHarness();
        Properties props = new Properties();
        props.load(java.nio.file.Files.newInputStream(SpeakerHarness.CONFIG.resolve("appleii.properties")));
        props.forEach((k, v) -> harness.parameter((String) k, (String) v));
        for (int i = 2; i < args.length; i++) {
            switch (args[i]) {
                case "--live" -> harness.live();
                // most software sits on a title screen until something is pressed
                case "--keys" -> harness.keys(2000, 0x0d, ' ', 0x0d, '1', 0x0d);
                default -> harness.parameter("diskDrive1", args[i]);
            }
        }

        byte[] pcm = harness.run(millis);
        SpeakerHarness.write(pcm, out);

        // the boot beep is skipped, what matters is whether the disk makes sound,
        // and how much of the time it does - a couple of error beeps look nothing
        // like a title tune
        short[] samples = SpeakerHarness.samples(pcm);
        int block = SPEAKER_SAMPLERATE / 10;
        int peak = 0, blocks = 0, loud = 0;
        for (int i = SPEAKER_SAMPLERATE * 2; i + block < samples.length; i += block) {
            double sum = 0;
            for (int j = i; j < i + block; j++) {
                peak = Math.max(peak, Math.abs(samples[j]));
                sum += (double) samples[j] * samples[j];
            }
            blocks++;
            if (Math.sqrt(sum / block) > 100) {
                loud++;
            }
        }

        System.err.printf("%s: %.2fs of sound in %.2fs, after boot: peak=%d sounding=%.1f%%%n",
                out.getFileName(),
                pcm.length / (double) (SPEAKER_SAMPLERATE * SPEAKER_SAMPLESIZE),
                millis / 1000.0,
                peak, blocks == 0 ? 0 : 100.0 * loud / blocks);
        if (harness.getWrites() > 0) {
            System.err.printf("live: %d writes, %d underruns%n", harness.getWrites(), harness.getUnderruns());
        }
        System.exit(0);
    }
}
