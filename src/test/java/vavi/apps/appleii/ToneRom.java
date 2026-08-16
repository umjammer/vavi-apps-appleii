/*
 * Copyright (c) 2026 by umjammer, All rights reserved.
 *
 * Released under the GPL
 */

package vavi.apps.appleii;


/**
 * The smallest possible apple ii "rom that uses sound": a reset routine that does
 * nothing but toggle the speaker soft switch at $C030 in a timed loop, which is
 * exactly how every apple ii makes a tone.
 *
 * <pre>
 *  d000  ad 30 c0    lda $c030   ; 4 clocks, flip the speaker
 *  d003  a0 nn       ldy #nn     ; 2 clocks
 *  d005  88          dey         ; 2 clocks
 *  d006  d0 fd       bne $d005   ; 3 clocks taken, 2 not
 *  d008  4c 00 d0    jmp $d000   ; 3 clocks
 * </pre>
 * so one half period costs {@code 5 * nn + 8} clocks.
 *
 * @author umjammer
 */
public final class ToneRom {

    /** the rom image is mapped at $d000, and $ffff is its last byte */
    private static final int ROM_BASE = 0xd000;

    private ToneRom() {
    }

    /**
     * @param cpuSpeed cpu clock in kHz
     * @param frequency wanted tone in Hz
     * @return a rom image {@link EmAppleII#loadRom} accepts
     */
    public static byte[] create(int cpuSpeed, int frequency) {
        int delay = (clocksPerHalfPeriod(cpuSpeed, frequency) - 8) / 5;
        if (delay < 1 || delay > 0xff) {
            throw new IllegalArgumentException("frequency out of range: " + frequency);
        }

        byte[] rom = new byte[0x8000];

        int[] code = {
                0xad, 0x30, 0xc0,
                0xa0, delay,
                0x88,
                0xd0, 0xfd,
                0x4c, 0x00, 0xd0,
        };
        for (int i = 0; i < code.length; i++) {
            rom[i] = (byte) code[i];
        }

        // loadRom() only takes an image whose $e000 holds a basic entry point
        rom[0xe000 - ROM_BASE] = 0x4c;

        // reset vector
        rom[0xfffc - ROM_BASE] = 0x00;
        rom[0xfffd - ROM_BASE] = (byte) 0xd0;

        return rom;
    }

    /** the tone the rom really produces, which the delay loop quantises */
    public static double actualFrequency(int cpuSpeed, int frequency) {
        int clocks = ((clocksPerHalfPeriod(cpuSpeed, frequency) - 8) / 5) * 5 + 8;
        return cpuSpeed * 1000.0 / (2 * clocks);
    }

    private static int clocksPerHalfPeriod(int cpuSpeed, int frequency) {
        return (int) (cpuSpeed * 1000.0 / (2 * frequency));
    }
}
