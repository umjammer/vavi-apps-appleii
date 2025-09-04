/*
 * AppleIIGo
 * The Java Apple II Emulator
 * (C) 2006 by Marc S. Ressl(ressl@lonetree.com)
 * Released under the GPL
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
    private AppleSpeaker speaker;
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
        speaker.setVolume(speaker.getVolume() + (up ? 1 : -1));
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
        display.requestRefresh();
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
        display.requestRefresh();
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
        setStatMode(getParameter("displayStatMode", "false").equals("true"));
        setGlare(getParameter("displayGlare", "false").equals("true"));

        // Speaker
        speaker = new AppleSpeaker(apple);
        speaker.setVolume(Integer.parseInt(getParameter("speakerVolume", "3")));

        // Peripherals
        disk = new DiskII();
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
//        speaker.setPaused(isCpuPaused);
    }

    /**
     * Resume emulator
     */
    public void resume() {
        logger.log(Level.TRACE, "resume()");
        isCpuPaused = false;
//        speaker.setPaused(isCpuPaused);
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
            disk.readDisk(dao, drive, resource, 254, false);

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
            disk.writeDisk(drive, diskDriveResource[drive]);
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
