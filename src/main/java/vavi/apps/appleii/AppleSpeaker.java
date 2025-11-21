/*
 * AppleIIGo
 * Speaker processing
 * Copyright 2006 by Marc S. Ressl(mressl@gmail.com)
 * Released under the GPL
 */

package vavi.apps.appleii;


public class AppleSpeaker implements Runnable {

    // Instances of other classes
    private final EmAppleII apple;

    // Refresh
//    private int refreshRate;
    private long refreshInterval;

    // Sound stuff
    public static final int SPEAKER_BITS = 16;
    public static final int SPEAKER_SAMPLERATE = 44100;
    public static final int SPEAKER_CHANNELS = 1;
    public static final int SPEAKER_SAMPLESIZE = (SPEAKER_BITS * SPEAKER_CHANNELS / 8);
    public static final boolean SPEAKER_SIGNED = true;
    public static final boolean SPEAKER_BIGENDIAN = false;

    private int clock, clockNextFlip, clockEnd;
    private boolean isFlipsBufferEmpty = true;

    private int bufferSize;
    private byte[] buffer;

    private int speakerVolume;
    private int speakerFlipsPointer;
    private int speakerFlipState;

    private final int[] speakerFlipStateToVolume = new int[2];
    private int speakerClocksPerSample;

    // Thread stuff
    private boolean isPaused = true;
//    private Thread thread;

    public AppleSpeaker(EmAppleII apple) {
        this.apple = apple;

        setVolume(4);
    }

    /**
     * Set refresh rate
     *
     * @param value Speaker refresh rate in mHz
     */
    private void setRefreshRate(int value) {
        if (value <= 0.0f)
            return;

//		this.refreshRate = value;
        refreshInterval = (int) (1000.0 / value);

        speakerClocksPerSample = (int) (apple.getCpuSpeed() * 1000.0f / SPEAKER_SAMPLERATE);
    }

//    /**
//     * Get refresh rate
//     */
//    private int getRefreshRate() {
//        return refreshRate;
//    }

    /**
     * Set speaker volume
     */
    public void setVolume(int value) {
        if ((value < 0) || (value > 7))
            return;

        speakerVolume = value;

        int absVolume = 1 << (value + 8);
        speakerFlipStateToVolume[0] = -absVolume;
        speakerFlipStateToVolume[1] = absVolume;
    }

    /**
     * Get speaker volume
     */
    public int getVolume() {
        return speakerVolume;
    }

    /**
     * Set pause state
     */
    public void setPaused(boolean value) {
        if (isPaused == value)
            return;

        isPaused = value;
        if (isPaused) {
//            try {
//                thread.join(1000);
//            } catch (InterruptedException e) {
//            }
            if (apple.view.isAudioAvailable()) {
                apple.view.closeAudio();
            }
        } else {
            setRefreshRate(apple.getRefreshRate());

            bufferSize = apple.view.initAudio();
            buffer = new byte[bufferSize];

            // TODO: this thread is not created any more (nick)
            //thread = new Thread(this);
            //thread.start();
        }
    }

    /**
     * Speaker refresh thread
     * TODO: this thread is not created any more (nick)
     */
    @Override
    public void run() {
        try {
            while (!isPaused) {
                long refreshStart = System.currentTimeMillis();
                long refreshDelay;

                refreshSpeaker();

                refreshDelay = System.currentTimeMillis() - refreshStart;

                if (refreshDelay < refreshInterval)
                    Thread.sleep(refreshInterval - refreshDelay);
            }
        } catch (InterruptedException e) {
        }
    }

    /**
     * Speaker refresh
     */
    public void refreshSpeaker() {
        clockEnd = apple.clock;
        int bytes;

        if (!apple.view.isAudioAvailable())
            return;

        while ((bytes = fillBuffer()) > 0) {
            apple.view.audioWrite(buffer, 0, bytes);
        }
    }

    /**
     * Fill buffer
     */
    private int fillBuffer() {
        int value = speakerFlipStateToVolume[speakerFlipState];
        int clockEndSample = clockEnd - speakerClocksPerSample;
        int bufferPointer = 0;

        initNextFlip();
        while (bufferPointer < bufferSize) {
            if (clockEndSample == clock)
                break;

            if (((clockEndSample - clock) & 0x7fff_ffff) > 0x3fff_ffff)
                break;

            // Find all flips on current sample
            while (((clockNextFlip - clock) & 0x7fff_ffff) < speakerClocksPerSample) {
                getNextFlip();
                speakerFlipState = (speakerFlipState ^ 1);
                value = speakerFlipStateToVolume[speakerFlipState];
            }

            // Write sample
            buffer[bufferPointer] = (byte) (value & 0xff);
            buffer[bufferPointer + 1] = (byte) (value >> 8);
            bufferPointer += SPEAKER_SAMPLESIZE;

            clock += speakerClocksPerSample;
        }

        return bufferPointer;
    }

    /**
     * Reset next flip
     */
    private void initNextFlip() {
        if (isFlipsBufferEmpty) {
            isFlipsBufferEmpty = false;
            getNextFlip();
        }
    }

    /**
     * Gets next flip
     */
    private void getNextFlip() {
        if (speakerFlipsPointer == apple.speakerFlipsPointer) {
            clockNextFlip = clock + 0x3fff_ffff;
            isFlipsBufferEmpty = true;
        } else {
            clockNextFlip = apple.speakerFlips[speakerFlipsPointer];
            speakerFlipsPointer = (speakerFlipsPointer + 1) & EmAppleII.SPEAKER_FLIPS_MASK;
        }
    }
}
