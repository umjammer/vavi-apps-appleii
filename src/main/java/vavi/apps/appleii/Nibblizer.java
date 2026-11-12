/*
 * AppleIIGo
 * Disk II Emulator
 * Copyright 2015 by Nick Westgate (Nick.Westgate@gmail.com)
 * Copyright 2006 by Marc S. Ressl(mressl@gmail.com)
 * Released under the GPL
 * Based on work by Doug Kwan
 */

package vavi.apps.appleii;


/**
 * Converts 16 sector tracks (256 bytes x 16, in physical sector order)
 * into a 6&amp;2 GCR encoded nibble track as seen by the Disk II controller.
 * <p>
 * Extracted from {@link DiskII}.
 */
final class Nibblizer {

    /** sectors per track */
    static final int NUM_SECTORS = 16;

    /** GCR encoding table */
    private static final int[] gcrEncodingTable = {
            0x96, 0x97, 0x9a, 0x9b, 0x9d, 0x9e, 0x9f, 0xa6,
            0xa7, 0xab, 0xac, 0xad, 0xae, 0xaf, 0xb2, 0xb3,
            0xb4, 0xb5, 0xb6, 0xb7, 0xb9, 0xba, 0xbb, 0xbc,
            0xbd, 0xbe, 0xbf, 0xcb, 0xcd, 0xce, 0xcf, 0xd3,
            0xd6, 0xd7, 0xd9, 0xda, 0xdb, 0xdc, 0xdd, 0xde,
            0xdf, 0xe5, 0xe6, 0xe7, 0xe9, 0xea, 0xeb, 0xec,
            0xed, 0xee, 0xef, 0xf2, 0xf3, 0xf4, 0xf5, 0xf6,
            0xf7, 0xf9, 0xfa, 0xfb, 0xfc, 0xfd, 0xfe, 0xff,
    };
    private static final int[] gcrSwapBit = {0, 2, 1, 3};

    /** nibble used to pad a track, skipped by the emulator on read */
    static final int PAD_NIBBLE = 0x7f;

    private final int[] gcrBuffer = new int[256];
    private final int[] gcrBuffer2 = new int[86];

    private byte[] gcrNibbles;
    private int gcrNibblesPos;

    /**
     * Converts a track to nibbles.
     *
     * @param track physical-ordered sector data, sector n is at {@code n * 256}
     * @param nibbles output, the remaining area is padded with {@link #PAD_NIBBLE}
     */
    void trackToNibbles(byte[] track, byte[] nibbles, int volumeNum, int trackNum) {
        this.gcrNibbles = nibbles;
        gcrNibblesPos = 0;

        for (int sectorNum = 0; sectorNum < NUM_SECTORS; sectorNum++) {
            encode62(track, sectorNum << 8);
            writeSync(12);
            writeAddressField(volumeNum, trackNum, sectorNum);
            writeSync(8);
            writeDataField();
        }
        writeNibbles(PAD_NIBBLE, nibbles.length - gcrNibblesPos); // invalid nibbles to skip on read
    }

    private void gcrWriteNibble(int value) {
        gcrNibbles[gcrNibblesPos] = (byte) value;
        gcrNibblesPos++;
    }

    private void writeNibbles(int nibble, int length) {
        while (length > 0) {
            length--;
            gcrWriteNibble(nibble);
        }
    }

    private void writeSync(int length) {
        writeNibbles(0xff, length);
    }

    /** Write an FM encoded value, used in writing address fields */
    private void encode44(int value) {
        gcrWriteNibble((value >> 1) | 0xaa);
        gcrWriteNibble(value | 0xaa);
    }

    /** Encode in 6:2 */
    private void encode62(byte[] track, int offset) {
        // 86 * 3 = 258, so the first two byte are encoded twice
        gcrBuffer2[0] = gcrSwapBit[track[offset + 1] & 0x03];
        gcrBuffer2[1] = gcrSwapBit[track[offset] & 0x03];

        // Save higher 6 bits in gcrBuffer and lower 2 bits in gcrBuffer2
        for (int i = 255, j = 2; i >= 0; i--, j = j == 85 ? 0 : j + 1) {
            gcrBuffer2[j] = ((gcrBuffer2[j] << 2) | gcrSwapBit[track[offset + i] & 0x03]);
            gcrBuffer[i] = (track[offset + i] & 0xff) >> 2;
        }

        // Clear off higher 2 bits of GCR_buffer2 set in the last call
        for (int i = 0; i < 86; i++) {
            gcrBuffer2[i] &= 0x3f;
        }
    }

    private void writeAddressField(int volumeNum, int trackNum, int sectorNum) {
        // Write address mark
        gcrWriteNibble(0xd5);
        gcrWriteNibble(0xaa);
        gcrWriteNibble(0x96);

        // Write volume, trackNum, sector & checksum
        encode44(volumeNum);
        encode44(trackNum);
        encode44(sectorNum);
        encode44(volumeNum ^ trackNum ^ sectorNum);

        // Write epilogue
        gcrWriteNibble(0xde);
        gcrWriteNibble(0xaa);
        gcrWriteNibble(0xeb);
    }

    private void writeDataField() {
        int last = 0;
        int checksum;

        // Write prologue
        gcrWriteNibble(0xd5);
        gcrWriteNibble(0xaa);
        gcrWriteNibble(0xad);

        // Write GCR encoded data
        for (int i = 0x55; i >= 0; i--) {
            checksum = last ^ gcrBuffer2[i];
            gcrWriteNibble(gcrEncodingTable[checksum]);
            last = gcrBuffer2[i];
        }
        for (int i = 0; i < 256; i++) {
            checksum = last ^ gcrBuffer[i];
            gcrWriteNibble(gcrEncodingTable[checksum]);
            last = gcrBuffer[i];
        }

        // Write checksum
        gcrWriteNibble(gcrEncodingTable[last]);

        // Write epilogue
        gcrWriteNibble(0xde);
        gcrWriteNibble(0xaa);
        gcrWriteNibble(0xeb);
    }
}
