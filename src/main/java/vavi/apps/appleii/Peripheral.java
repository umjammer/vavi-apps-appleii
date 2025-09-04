/*
 * AppleIIGo
 * Slot interface
 * Copyright 2006 by Marc S. Ressl(mressl@gmail.com)
 * Released under the GPL
 * Based on work by Steven E. Hugg
 */

package vavi.apps.appleii;

import java.util.Random;


public class Peripheral {

    protected final Random rand = new Random();

    public Peripheral() {
    }

    /**
     * I/O read
     *
     * @param address Address
     */
    public int ioRead(int address) {
        return rand.nextInt(256);
    }

    /**
     * I/O write
     *
     * @param address Address
     */
    public void ioWrite(int address, int value) {
    }

    /**
     * Memory read
     *
     * @param address Address
     */
    public int memoryRead(int address) {
        return 0;
    }

    public void memoryWrite(int address, int value) {
    }

    /**
     * Reset peripheral
     */
    public void reset() {
    }
}
