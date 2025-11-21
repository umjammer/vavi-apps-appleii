/*
 * AppleIIGo
 * The Java Apple II Emulator
 * (C) 2006 by Marc S. Ressl(ressl@lonetree.com)
 * Copyright 2015 by Nick Westgate (Nick.Westgate@gmail.com)
 * Released under the GNU General Public License version 2
 * See http://www.gnu.org/licenses/
 * <p>
 * Change list:
 * <p>
 * Version 1.0.10 - changes by Nick:
 * - fixed disk stepping bug for Mabel's Mansion using my code from AppleWin
 * - patch loaded ROM's empty slots with faux-floating bus data so Mabel's Mansion works
 * - revert CPU status bug introduced in 1.0.9 - V and R used the same bit
 * - fixed BRK bug by adding extra PC increment
 * - NOTE: decimal mode arithmetic fails some tests and should be fixed someday
 * <p>
 * Version 1.0.9 - changes by Nick:
 * - fixed disk speed-up bug (Sherwood Forest reads with the drive motor off)
 * - added check for 2IMG header ID
 * - fixed processor status bugs in BRK, PLP, RTI, NMI, IRQ
 * <p>
 * Version 1.0.8 - changes by Nick:
 * - implemented disk writing (only in memory, not persisted)
 * - added support for .2MG (2IMG) disk images, including lock flag and volume number
 * - support meta tag for write protect in disk filename eg: NotWritable_Meta_DW0.dsk
 * <p>
 * Version 1.0.7 - changes by Nick:
 * - fixed disk emulation bug (sense write protect entered write mode)
 * - now honour diskWritable parameter (but writing is not implemented)
 * - support meta tag for volume number in disk filename eg: Vol2_Meta_DV2.dsk
 * - added isPaddleEnabled parameter
 * - exposed setPaddleEnabled(boolean value), setPaddleInverted(boolean value)
 * - paddle values are now 255 at startup (ie. correct if disabled/not present)
 * - minor vavi.apps.appleii.AppleSpeaker fix (SourceDataLine.class) thanks to William Halliburton
 * <p>
 * Version 1.0.6 - changes by Nick:
 * - exposed F3/F4 disk swapping method: cycleDisk(int driveNumber)
 * - exposed reset() method
 * - exposed setSpeed(int value) method
 * <p>
 * Version 1.0.5 - changes by Nick:
 * - added support for .NIB (nibble) disk images  (also inside ZIP archives)
 * - added disk speedup hacks for DOS (expect ~2x faster reads)
 * <p>
 * Version 1.0.4 - changes by Nick:
 * - added support for .PO (ProDOS order) disk images (also inside ZIP archives)
 * - added Command key for Closed-Apple on Mac OS X
 * - added Home and End keys for Open-Apple and Closed-Apple on full keyboards
 * <p>
 * Version 1.0.3 - changes by Nick:
 * - fixed paddle values for scaled display window
 * - added "digital" joystick support via numeric keypad arrows
 * - added Left-Alt and Right-Alt keys for Open-Apple and Closed-Apple
 * - changed reset key from Home to Ctrl-F12 and Ctrl-Pause/Break
 * <p>
 * Version 1.0.2 - changes by Nick:
 * - improved sound sync by moving vavi.apps.appleii.AppleSpeaker into the main thread
 * - added version (F1)
 * - added multiple disks & swapping (F3, F4)
 * - added ZIP archive support
 * - fixed HTTP disk image access bug
 */

package vavi.apps.appleii;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;

import static java.lang.System.getLogger;


/**
 * AppleIIGo class<p>
 * Connects EmAppleII, AppleCanvas
 */
public class AppleIIGo {

    private static final Logger logger = getLogger(AppleIIGo.class.getName());

    // Class instances
    private EmAppleII apple;
    private AppleDisplay display;
    private DiskII disk;

    // Machine variables
    private boolean isCpuPaused;
    private boolean isCpuDebugEnabled;

    /** */
    public boolean isCpuDebugEnabled() {
        return isCpuDebugEnabled;
    }

    // Keyboard variables
    private boolean keyboardUppercaseOnly;

    // Paddle variables
    private boolean isPaddleInverted;

    // Disk variables
    private final String[] diskDriveResource = new String[2];

    public String getDiskDriveResource(int drive) {
        return diskDriveResource[drive];
    }

    private boolean diskWritable;

    /** abstraction for view functionality */
    public interface View {

        /** */
        int[] createImageBuffer();

        /** */
        void repaint();

        /** */
        void getCharSet(int[] buffer, int w, int h, int s);

        /** */
        void setDisplayScaledSizeX(int w);

        /** */
        void setDisplayScaledSizeY(int h);

        /** */
        void debug(Throwable t);

        /** */
        void debug(String s);

        /** @return audio buffer size */
        int initAudio();

        boolean isAudioAvailable();

        void closeAudio();

        void audioWrite(byte[] buffer, int offset, int length);
    }

    /** */
    public void setView(View view) {
        this.view = view;
    }

    /** */
    private View view;

    /** abstraction for dao functionality */
    public interface Dao {

        /** */
        String getParameter(String parameter);

        /**
         * Open input stream
         */
        void openInputStream(String resource);

        /** */
        void read(byte[] bytes, int offset, int length);

        /**
         * Open input stream
         */
        void closeInputStream();
    }

    /** */
    public void setDao(Dao dao) {
        this.dao = dao;
    }

    /** */
    private Dao dao;

    public void setKeyLatch(int key) {
        if (key < 128) {
            if (keyboardUppercaseOnly && (key >= 97) && (key <= 122)) {
                key -= 32;
            }
            apple.setKeyLatch(key);
        }
    }

    public void setButton(int button, boolean flag) {
        apple.paddle.setButton(button, flag);
    }

    /** for key */
    public void setPaddle(int paddle, int value) {
        apple.paddle.setPaddlePos(paddle, value);
    }

    /** for mouse */
    public void setPaddlePos(int x, int y) {
        if (isPaddleInverted) {
            apple.paddle.setPaddlePos(0, (int) (display.getScale() * (255 - (float) (y * 256) / 192)));
            apple.paddle.setPaddlePos(1, (int) (display.getScale() * (255 - (float) (x * 256) / 280)));
        } else {
            apple.paddle.setPaddlePos(0, (int) (x * display.getScale() * 256 / 280));
            apple.paddle.setPaddlePos(1, (int) (y * display.getScale() * 256 / 192));
        }
    }

    public void setVolume(boolean up) {
        apple.speaker.setVolume(apple.speaker.getVolume() + (up ? 1 : -1));
    }

    public void toggleStatMode() {
        setStatMode(!isStatMode);
    }

    public void toggleStepMode() {
        apple.setStepMode(!apple.getStepMode());
    }

    public void stepInstructions(int step) {
        apple.setStepMode(apple.getStepMode());
        apple.stepInstructions(step);
    }

    public int[] getDisplayImageBuffer() {
        return display.getDisplayImageBuffer();
    }

    public boolean isPaused() {
        return display.isPaused();
    }

    private boolean isGlare;
    private boolean isStatMode;

    /**
     * Set glare
     */
    public void setGlare(boolean value) {
        isGlare = value;
        display.setGlare(false);
        display.setStatMode(false);
    }

    /**
     * Get glare
     */
    public boolean isGlare() {
        return isGlare;
    }

    /**
     * Set stat mode
     */
    public void setStatMode(boolean value) {
        isStatMode = value;
        display.setGlare(false);
        display.setStatMode(false);
    }

    /**
     * Get stat mode
     */
    public boolean isStatMode() {
        return isStatMode;
    }

    public String getStatInfo() {
        return apple.getStatInfo() + "\n" + display.getStatInfo();
    }

    /**
     * Parameters
     */
    public String getParameter(String parameter, String defaultValue) {
        String value = dao.getParameter(parameter);
        if ((value == null) || (value.isEmpty())) {
            return defaultValue;
        }
        return value;
    }

    /**
     * On applet initialization
     */
    public void init() {
        logger.log(Level.TRACE, "init()");

        // Activate listeners

        // Initialize Apple II emulator
        apple = new EmAppleII(view);
        loadRom(getParameter("cpuRom", ""));
        apple.setCpuSpeed(Integer.parseInt(getParameter("cpuSpeed", "1000")));
        isCpuPaused = getParameter("cpuPaused", "false").equals("true");
        isCpuDebugEnabled = getParameter("cpuDebugEnabled", "false").equals("true");
        apple.setStepMode(getParameter("cpuStepMode", "false").equals("true"));

        // Keyboard
        keyboardUppercaseOnly = getParameter("keyboardUppercaseOnly", "true").equals("true");

        // Display
        display = new AppleDisplay(apple);
        display.setScale(Float.parseFloat(getParameter("displayScale", "1")));
        display.setRefreshRate(Integer.parseInt(getParameter("displayRefreshRate", "10")));
        display.setColorMode(Integer.parseInt(getParameter("displayColorMode", "1")));
        display.setStatMode(getParameter("displayStatMode", "false").equals("true"));
        display.setGlare(getParameter("displayGlare", "false").equals("true"));

        // Speaker
        apple.speaker = new AppleSpeaker(apple);
        apple.speaker.setVolume(Integer.parseInt(getParameter("speakerVolume", "3")));

        // Peripherals
        disk = new DiskII(apple);
        apple.setPeripheral(disk, 6);

        // Initialize disk drives
        diskWritable = getParameter("diskWritable", "false").equals("true");
        mountDisk(0, getParameter("diskDrive1", ""));
        mountDisk(1, getParameter("diskDrive2", ""));
    }

    public void start() {
        // Start CPU
        if (!isCpuPaused) {
            resume();
        }
    }

    /**
     * On applet destruction
     */
    public void destroy() {
        logger.log(Level.TRACE, "destroy()");
        unmountDisk(0);
        unmountDisk(1);
    }

    /**
     * Pause emulator
     */
    public void pause() {
        logger.log(Level.TRACE, "pause()");
        isCpuPaused = true;
        apple.setPaused(isCpuPaused);
        display.setPaused(isCpuPaused);
        apple.speaker.setPaused(isCpuPaused);
    }

    /**
     * Resume emulator
     */
    public void resume() {
        logger.log(Level.TRACE, "resume()");
        isCpuPaused = false;
        apple.speaker.setPaused(isCpuPaused);
        display.setPaused(isCpuPaused);
        apple.setPaused(isCpuPaused);
    }

    /**
     * Restarts emulator
     */
    public void restart() {
        logger.log(Level.TRACE, "restart()");
        apple.restart();
    }

    /**
     * Resets emulator
     */
    public void reset() {
        logger.log(Level.TRACE, "reset()");
        apple.reset();
    }

    /**
     * Load ROM
     */
    public void loadRom(String resource) {
        logger.log(Level.TRACE, "loadRom(resource: " + resource + ")");
        apple.loadRom(dao, resource);
    }

    /**
     * Mount a disk
     */
    public boolean mountDisk(int drive, String resource) {
        logger.log(Level.TRACE, "mountDisk(drive: " + drive + ", resource: " + resource + ")");

        if ((drive < 0) || (drive > 2)) {
            return false;
        }

        try {
            unmountDisk(drive);

            diskDriveResource[drive] = resource;

            logger.log(Level.TRACE, "mount: dirve: " + drive + ", " + resource);
            disk.readDisk(dao, drive, resource, false, 254);

            return true;
        } catch (Throwable e) {
            if (e instanceof IllegalStateException) {
                logger.log(Level.WARNING, "mount: drive: " + drive + ": no disk, " + e.getMessage());
            } else {
                logger.log(Level.ERROR, e.getMessage(), e);
            }
            return false;
        }
    }

    /**
     * Unmount a disk
     */
    public void unmountDisk(int drive) {
        logger.log(Level.TRACE, "unmount: drive: " + drive);
        if ((drive < 0) || (drive > 2)) {
            return;
        }

        if (!diskWritable) {
            logger.log(Level.TRACE, "unmount: drive: " + drive + ", not writable");
            return;
        }

        try {
            disk.writeDisk(drive, dao);
        } catch (Throwable e) {
            if (e instanceof NullPointerException) {
                logger.log(Level.WARNING, "unmount: drive: " + drive + ": no disk, " + e.getMessage());
            } else {
                logger.log(Level.ERROR, e.getMessage(), e);
            }
        }
    }

    /**
     * Set color mode
     */
    public void setColorMode(int value) {
        logger.log(Level.TRACE, "setColorMode(value: " + value + ")");
        display.setColorMode(value);
    }

    /**
     * Get disk activity
     */
    public boolean getDiskActivity() {
        return (!isCpuPaused && disk.isMotorOn());
    }
}
