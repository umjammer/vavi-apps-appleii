/*
 * AppleIIGo
 * Disk II Emulator
 * Copyright 2015 by Nick Westgate (Nick.Westgate@gmail.com)
 * Copyright 2006 by Marc S. Ressl(mressl@gmail.com)
 * Released under the GPL
 * Based on work by Doug Kwan
 */

package vavi.apps.appleii;

import java.lang.System.Logger;

import static java.lang.System.getLogger;


public class DiskII extends Peripheral {

    private static final Logger logger = getLogger(DiskII.class.getName());

    /** ROM (with boot wait cycle optimization) */
    private static final int[] rom = {
            0xa2, 0x20, 0xa0, 0x00, 0xa2, 0x03, 0x86, 0x3c, 0x8a, 0x0a, 0x24, 0x3c, 0xf0, 0x10, 0x05, 0x3c,
            0x49, 0xff, 0x29, 0x7e, 0xb0, 0x08, 0x4a, 0xd0, 0xfb, 0x98, 0x9d, 0x56, 0x03, 0xc8, 0xe8, 0x10,
            0xe5, 0x20, 0x58, 0xff, 0xba, 0xbd, 0x00, 0x01, 0x0a, 0x0a, 0x0a, 0x0a, 0x85, 0x2b, 0xaa, 0xbd,
            0x8e, 0xc0, 0xbd, 0x8c, 0xc0, 0xbd, 0x8a, 0xc0, 0xbd, 0x89, 0xc0, 0xa0, 0x50, 0xbd, 0x80, 0xc0,
            0x98, 0x29, 0x03, 0x0a, 0x05, 0x2b, 0xaa, 0xbd, 0x81, 0xc0, 0xa9, 0x56, 0xa9, 0x00, 0xea, 0x88,
            0x10, 0xeb, 0x85, 0x26, 0x85, 0x3d, 0x85, 0x41, 0xa9, 0x08, 0x85, 0x27, 0x18, 0x08, 0xbd, 0x8c,
            0xc0, 0x10, 0xfb, 0x49, 0xd5, 0xd0, 0xf7, 0xbd, 0x8c, 0xc0, 0x10, 0xfb, 0xc9, 0xaa, 0xd0, 0xf3,
            0xea, 0xbd, 0x8c, 0xc0, 0x10, 0xfb, 0xc9, 0x96, 0xf0, 0x09, 0x28, 0x90, 0xdf, 0x49, 0xad, 0xf0,
            0x25, 0xd0, 0xd9, 0xa0, 0x03, 0x85, 0x40, 0xbd, 0x8c, 0xc0, 0x10, 0xfb, 0x2a, 0x85, 0x3c, 0xbd,
            0x8c, 0xc0, 0x10, 0xfb, 0x25, 0x3c, 0x88, 0xd0, 0xec, 0x28, 0xc5, 0x3d, 0xd0, 0xbe, 0xa5, 0x40,
            0xc5, 0x41, 0xd0, 0xb8, 0xb0, 0xb7, 0xa0, 0x56, 0x84, 0x3c, 0xbc, 0x8c, 0xc0, 0x10, 0xfb, 0x59,
            0xd6, 0x02, 0xa4, 0x3c, 0x88, 0x99, 0x00, 0x03, 0xd0, 0xee, 0x84, 0x3c, 0xbc, 0x8c, 0xc0, 0x10,
            0xfb, 0x59, 0xd6, 0x02, 0xa4, 0x3c, 0x91, 0x26, 0xc8, 0xd0, 0xef, 0xbc, 0x8c, 0xc0, 0x10, 0xfb,
            0x59, 0xd6, 0x02, 0xd0, 0x87, 0xa0, 0x00, 0xa2, 0x56, 0xca, 0x30, 0xfb, 0xb1, 0x26, 0x5e, 0x00,
            0x03, 0x2a, 0x5e, 0x00, 0x03, 0x2a, 0x91, 0x26, 0xc8, 0xd0, 0xee, 0xe6, 0x27, 0xe6, 0x3d, 0xa5,
            0x3d, 0xcd, 0x00, 0x08, 0xa6, 0x2b, 0x90, 0xdb, 0x4c, 0x01, 0x08, 0x00, 0x00, 0x00, 0x00, 0x00,
    };

    // Constants
    public static final int DEFAULT_VOLUME = 254;
    private static final int NUM_DRIVES = 2;
    private static final int DOS_NUM_SECTORS = 16;
    private static final int DOS_NUM_TRACKS = 35;
    private static final int MAX_PHYS_TRACK = (2 * DOS_NUM_TRACKS) - 1;
    private static final int DOS_TRACK_BYTES = 256 * DOS_NUM_SECTORS;
    private static final int RAW_TRACK_BYTES = 0x1A00; // 0x1A00 (6656) for .NIB (was 6250)
    private static final int STANDARD_2IMG_HEADER_ID = 0x32494D47;
    private static final int STANDARD_2IMG_HEADER_SIZE = 64;
    private static final int STANDARD_PRODOS_BLOCKS = 280;

    // Disk II direct access variables
    private int drive = 0;
    private int phases = 0;
    private boolean isMotorOn = false;

    private final boolean[] isWriteProtected = new boolean[NUM_DRIVES];
    private final byte[][][] diskData = new byte[NUM_DRIVES][DOS_NUM_TRACKS][];

    private int currPhysTrack;
    private int currNibble;

    // Caches
    private final int[] driveCurrPhysTrack = new int[NUM_DRIVES];
    private byte[] realTrack;

    /*
     * Disk II emulation:
     *
     * C0xD, C0xE -> Read write protect
     * C0xE, C0xC -> Read data from disk
     * Write data to disk -> C0xF, C0xC
     * Write data to disk -> C0xD, C0xC
     *
     * We use 'fast mode', i.e. no 65(C)02 clock reference
     * We use simplified track handling (only adjacent phases)
     */

    // Internal registers
    private int latchData;
    private boolean writeMode;
    private boolean loadMode;
    private int driveSpin;

    /** GCR encoding and decoding tables */
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
    //private int[] gcrDecodingTable = new int[256];
    private final int[] gcrSwapBit = {0, 2, 1, 3};
    private final int[] gcrBuffer = new int[256];
    private final int[] gcrBuffer2 = new int[86];

    /** Physical sector to DOS 3.3 logical sector table */
    private static final int[] gcrLogicalDos33Sector = {
            0x0, 0x7, 0xe, 0x6, 0xd, 0x5, 0xc, 0x4,
            0xb, 0x3, 0xa, 0x2, 0x9, 0x1, 0x8, 0xf
    };
    /** Physical sector to DOS 3.3 logical sector table */
    private static final int[] gcrLogicalProdosSector = {
            0x0, 0x8, 0x1, 0x9, 0x2, 0xa, 0x3, 0xb,
            0x4, 0xc, 0x5, 0xd, 0x6, 0xe, 0x7, 0xf
    };

    // Temporary variables for conversion
    private byte[] gcrNibbles = new byte[RAW_TRACK_BYTES];
    private int gcrNibblesPos;

    private final EmAppleII apple;

    /**
     * Constructor
     */
    public DiskII(EmAppleII apple) {
        this.apple = apple;

        readDisk(null, 0, "", false, DEFAULT_VOLUME);
        readDisk(null, 1, "", false, DEFAULT_VOLUME);
    }

    /**
     * I/O read
     *
     * @param address Address
     */
    @Override
    public int ioRead(int address) {
        switch (address & 0xf) {
            case 0x0:
            case 0x1:
            case 0x2:
            case 0x3:
            case 0x4:
            case 0x5:
            case 0x6:
            case 0x7:
                setPhase(address);
                break;
            case 0x8:
                isMotorOn = false;
                break;
            case 0x9:
                isMotorOn = true;
                break;
            case 0xa:
                setDrive(0);
                break;
            case 0xb:
                setDrive(1);
                break;
            case 0xc:
                ioLatchC();
                break;
            case 0xd:
                loadMode = true;
                if (isMotorOn && !writeMode) {
                    latchData &= 0x7F;
                    // TODO: check phase - write protect is forced if phase 1 is on [F9.7]
                    if (isWriteProtected[drive]) {
                        latchData |= 0x80;
                    }
                }
                break;
            case 0xe:
                writeMode = false;
                break;
            case 0xf:
                writeMode = true;
                break;
        }

        // only even addresses return the latch
        return ((address & 1) == 0) ? latchData : rand.nextInt(256); // TODO: floating bus
    }

    @Override
    public void ioWrite(int address, int value) {
        switch (address & 0xf) {
            case 0x0:
            case 0x1:
            case 0x2:
            case 0x3:
            case 0x4:
            case 0x5:
            case 0x6:
            case 0x7:
                setPhase(address);
                break;
            case 0x8:
                isMotorOn = false;
                break;
            case 0x9:
                isMotorOn = true;
                break;
            case 0xa:
                setDrive(0);
                break;
            case 0xb:
                setDrive(1);
                break;
            case 0xc:
                ioLatchC();
                break;
            case 0xd:
                loadMode = true;
                break;
            case 0xe:
                writeMode = false;
                break;
            case 0xf:
                writeMode = true;
                break;
        }

        if (isMotorOn && writeMode && loadMode) {
            // any address writes latch for sequencer LD; OE1/2 irrelevant ['323 datasheet]
            latchData = value;
        }
    }

    @Override
    public int memoryRead(int address) {
        return rom[address & 0xff];
    }

    @Override
    public void reset() {
        drive = 0;
        isMotorOn = false;
        loadMode = false;
        writeMode = false;
    }

    /**
     * Loads a disk
     *
     * @param name  filename
     * @param drive Disk II drive
     */
    public boolean readDisk(AppleIIGo.Dao dao, int drive, String name, boolean isWriteProtected, int volumeNumber) {
        byte[] track = new byte[DOS_TRACK_BYTES];
        boolean proDos = false;
        boolean nib = false;

        if (dao != null) {
            dao.openInputStream(name);
        }

        String lowerName = name.toLowerCase();
        if (lowerName.contains(".2mg") || lowerName.contains(".2img")) {
            // 2IMG, so check if we can handle it
            byte[] header = new byte[STANDARD_2IMG_HEADER_SIZE];
            dao.read(header, 0, STANDARD_2IMG_HEADER_SIZE);

            int id = (header[0x00] << 24) | (header[0x01] << 16) | (header[0x02] << 8) | (header[0x03]);
            if (id != STANDARD_2IMG_HEADER_ID)
                return false;

            int headerSize = (header[0x09] << 8) | (header[0x08]);
            if (headerSize != STANDARD_2IMG_HEADER_SIZE)
                return false;

            int format = (header[0x0F] << 24) | (header[0x0E] << 16) | (header[0x0D] << 8) | (header[0x0C]);
            if (format == 1) {
                proDos = true;
                int blocks = (header[0x17] << 24) | (header[0x16] << 16) | (header[0x15] << 8) | (header[0x14]);
                if (blocks != STANDARD_PRODOS_BLOCKS)
                    return false; // only handle standard 5.25 inch images
            } else if (format == 2) {
                nib = true;
            } else if (format != 0) {
                return false; // if not ProDOS, NIB or DSK
            }

            // use write protected and volume number if present
            int flags = (header[0x13] << 24) | (header[0x12] << 16) | (header[0x11] << 8) | (header[0x10]);
            if ((flags & (1 << 31)) != 0) {
                isWriteProtected = true; // only override if set
            }
            if ((flags & (1 << 8)) != 0) {
                volumeNumber = (flags & 0xFF);
            }
        } else {
            // check for PO and NIB in the name
            proDos = lowerName.contains(".po");
            nib = lowerName.contains(".nib");
        }

        for (int trackNum = 0; trackNum < DOS_NUM_TRACKS; trackNum++) {
            diskData[drive][trackNum] = new byte[RAW_TRACK_BYTES];

            if (dao != null) {
                if (nib) {
                    dao.read(diskData[drive][trackNum], 0, RAW_TRACK_BYTES);
                } else {
                    dao.read(track, 0, DOS_TRACK_BYTES);
                    trackToNibbles(track, diskData[drive][trackNum], volumeNumber, trackNum, !proDos);
                }
            }
        }

        this.realTrack = diskData[drive][currPhysTrack >> 1];
        this.isWriteProtected[drive] = isWriteProtected;

        return true;
    }

    /**
     * Writes a disk
     *
     * @param dao   io
     * @param drive Disk II drive
     */
    public boolean writeDisk(int drive, AppleIIGo.Dao dao) {
        return true;
    }

    /**
     * Motor on indicator
     */
    public boolean isMotorOn() {
        return isMotorOn;
    }

    /**
     * I/O read Latch C
     */
    private void ioLatchC() {
        loadMode = false;
        if (!writeMode) {
            if (!isMotorOn) {
                // simple hack to fool RWTS SAMESLOT drive spin check (usually at $BD34)
                driveSpin = (driveSpin + 1) & 0xF;
                if (driveSpin == 0) {
                    latchData = 0x7F;
                    return;
                }
            }

            // Read data: C0xE, C0xC
            latchData = (realTrack[currNibble] & 0xff);

            // is RWTS looking for an address prologue? (RDADR)
            if (/* fastDisk && */ // TODO: fastDisk property to enable/disable
                    apple.memoryRead(apple.PC + 3) == 0xD5 && // #$D5
                            apple.memoryRead(apple.PC + 2) == 0xC9 && // CMP (usually at $B94F)
                            apple.memoryRead(apple.PC + 1) == 0xFB && // PC - 3
                            apple.memoryRead(apple.PC + 0) == 0x10 &&  // BPL
                            latchData != 0xD5) {
                // Y: find the next address prologues
                int count = RAW_TRACK_BYTES / 16;
                do {
                    currNibble++;
                    if (currNibble >= RAW_TRACK_BYTES)
                        currNibble = 0;
                    latchData = (realTrack[currNibble] & 0xff);
                }
                while (latchData != 0xD5 && --count > 0);
            }
            // N: skip invalid nibbles we padded the track buffer with
            else if (latchData == 0x7F) {
                int count = RAW_TRACK_BYTES / 16;
                do {
                    currNibble++;
                    if (currNibble >= RAW_TRACK_BYTES)
                        currNibble = 0;
                    latchData = (realTrack[currNibble] & 0xff);
                }
                while (latchData == 0x7F && --count > 0);
            }
        } else {
            // Write data: C0xD, C0xC
            realTrack[currNibble] = (byte) latchData;
        }

        /*
         * I/O write Latch D
         */
        currNibble++;
        if (currNibble >= RAW_TRACK_BYTES)
            currNibble = 0;
    }

    /**
     * I/O read Latch E
     */
    private void setPhase(int address) {
        int phase = (address >> 1) & 3;
        int phase_bit = (1 << phase);

        // update the magnet states
        if ((address & 1) != 0) {
            // phase on
            phases |= phase_bit;
        } else {
            // phase off
            phases &= ~phase_bit;
        }

        // check for any stepping effect from a magnet
        // - move only when the magnet opposite the cog is off
        // - move in the direction of an adjacent magnet if one is on
        // - do not move if both adjacent magnets are on
        // momentum and timing are not accounted for ... maybe one day!
        int direction = 0;
        if ((phases & (1 << ((currPhysTrack + 1) & 3))) != 0)
            direction += 1;
        if ((phases & (1 << ((currPhysTrack + 3) & 3))) != 0)
            direction -= 1;

        // apply magnet step, if any
        if (direction != 0) {
            currPhysTrack += direction;
            if (currPhysTrack < 0)
                currPhysTrack = 0;
            else if (currPhysTrack > MAX_PHYS_TRACK)
                currPhysTrack = MAX_PHYS_TRACK;
        }
        realTrack = diskData[drive][currPhysTrack >> 1];
    }

    /**
     * I/O write Latch F
     *
     * @param newDrive
     */
    private void setDrive(int newDrive) {
        driveCurrPhysTrack[drive] = currPhysTrack;
        drive = newDrive;
        currPhysTrack = driveCurrPhysTrack[drive];
        realTrack = diskData[drive][currPhysTrack >> 1];
    }

    /*
     * TRACK CONVERSION ROUTINES
     */

    /**
     * Writes a nibble
     *
     * @param value Value
     */
    private void gcrWriteNibble(int value) {
        gcrNibbles[gcrNibblesPos] = (byte) value;
        gcrNibblesPos++;
    }

    /**
     * Writes nibbles
     *
     * @param length Number of bits
     */
    private final void writeNibbles(int nibble, int length) {
        while (length > 0) {
            length--;
            gcrWriteNibble(nibble);
        }
    }

    /**
     * Writes sync nibbles
     *
     * @param length Number of bits
     */
    private final void writeSync(int length) {
        writeNibbles(0xff, length);
    }

    /**
     * Write an FM encoded value, used in writing address fields
     *
     * @param value Value
     */
    private void encode44(int value) {
        gcrWriteNibble((value >> 1) | 0xaa);
        gcrWriteNibble(value | 0xaa);
    }

    /**
     * Encode in 6:2
     *
     * @param track  Sectorized track data
     * @param offset Offset in this data
     */
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

    /**
     * Write address field
     *
     * @param trackNum  Sectorized track data
     * @param sectorNum Offset in this data
     */
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

    /**
     * Write data field
     */
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

    /**
     * Converts a track to nibbles
     */
    private void trackToNibbles(byte[] track, byte[] nibbles, int volumeNum, int trackNum, boolean dos) {
        this.gcrNibbles = nibbles;
        gcrNibblesPos = 0;
        int[] logicalSector = (dos) ? gcrLogicalDos33Sector : gcrLogicalProdosSector;

        for (int sectorNum = 0; sectorNum < DOS_NUM_SECTORS; sectorNum++) {
            encode62(track, logicalSector[sectorNum] << 8);
            writeSync(12);
            writeAddressField(volumeNum, trackNum, sectorNum);
            writeSync(8);
            writeDataField();
        }
        writeNibbles(0x7F, RAW_TRACK_BYTES - gcrNibblesPos); // invalid nibbles to skip on read
    }
}
