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
import java.lang.System.Logger.Level;

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
    private static final int DOS_NUM_TRACKS = DiskImage.NUM_TRACKS;
    private static final int MAX_PHYS_TRACK = (2 * DOS_NUM_TRACKS) - 1;

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
     * @param dao   io, null means no disk
     * @param drive Disk II drive
     * @param name  filename, null or empty means no disk
     * @return false when the image is not supported, the drive becomes empty
     */
    public boolean readDisk(AppleIIGo.Dao dao, int drive, String name, boolean isWriteProtected, int volumeNumber) {
        DiskImage image = DiskImage.empty();
        boolean result = true;

        if (dao != null && name != null && !name.isEmpty()) {
            dao.openInputStream(name);
            try {
                image = DiskImage.read(dao.readAllBytes(), name, isWriteProtected, volumeNumber);
                logger.log(Level.DEBUG, "drive " + drive + ": " + name + " (" + image.getFormat() + ")");
            } catch (RuntimeException e) {
                logger.log(Level.WARNING, "drive " + drive + ": unsupported image: " + name + ", " + e.getMessage());
                result = false;
            } finally {
                dao.closeInputStream();
            }
        }

        byte[][] tracks = image.getTracks();
        System.arraycopy(tracks, 0, diskData[drive], 0, DOS_NUM_TRACKS);
        if (drive == this.drive) {
            this.realTrack = diskData[drive][currPhysTrack >> 1];
        }
        this.isWriteProtected[drive] = image.isWriteProtected() || isWriteProtected;

        return result;
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
        // tracks may have different lengths (e.g. WOZ), keep the position inside the current one
        if (currNibble >= realTrack.length) {
            currNibble %= realTrack.length;
        }
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
                int count = realTrack.length / 16;
                do {
                    currNibble++;
                    if (currNibble >= realTrack.length)
                        currNibble = 0;
                    latchData = (realTrack[currNibble] & 0xff);
                }
                while (latchData != 0xD5 && --count > 0);
            }
            // N: skip invalid nibbles we padded the track buffer with
            else if (latchData == 0x7F) {
                int count = realTrack.length / 16;
                do {
                    currNibble++;
                    if (currNibble >= realTrack.length)
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
        if (currNibble >= realTrack.length)
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
}
