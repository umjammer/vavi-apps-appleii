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
    /** cpu clocks per sample, 16.16 fixed point: 44100 never divides the cpu clock evenly */
    private int speakerClocksPerSample;
    /** fractional part left over from the previous sample */
    private int speakerClockAccumulator;

    /**
     * DC blocking filter. An idle speaker holds a constant full scale level, and a
     * steady offset like that turns every start, stop and buffer underrun into a
     * pop. Filtering it out lets an untouched speaker decay to real silence, the
     * way the physical cone does.
     */
    private static final float DC_FILTER = 0.9985f; // ~10Hz at 44100Hz
    private float dcFilterLastIn, dcFilterLastOut;

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

        // never zero, or filling a buffer would never advance the clock
        speakerClocksPerSample = Math.max(1, (int) (apple.getCpuSpeed() * 1000.0 / SPEAKER_SAMPLERATE * 0x1_0000));
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

        int absVolume = Math.min(1 << (value + 8), Short.MAX_VALUE);
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

            // start from where the cpu is now, otherwise the whole paused span
            // would be rendered as one huge catch-up burst
            clock = apple.clock;
            speakerClockAccumulator = 0;
            speakerFlipsPointer = apple.speakerFlipsPointer;
            isFlipsBufferEmpty = true;
            // the filter starts already settled on the level the speaker sits at,
            // so starting up is silent rather than a click
            dcFilterLastIn = speakerFlipStateToVolume[speakerFlipState];
            dcFilterLastOut = 0;

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
        int bufferPointer = 0;

        initNextFlip();
        while (bufferPointer < bufferSize) {
            int accumulator = speakerClockAccumulator + speakerClocksPerSample;
            int clockStep = accumulator >> 16;

            // clock differences are computed as plain int subtraction so they stay
            // correct when the cpu clock wraps
            if (clockEnd - clock < clockStep)
                break;

            speakerClockAccumulator = accumulator & 0xffff;

            // Find all flips on current sample
            while (clockNextFlip - clock < clockStep) {
                getNextFlip();
                speakerFlipState = (speakerFlipState ^ 1);
                value = speakerFlipStateToVolume[speakerFlipState];
            }

            // Write sample
            dcFilterLastOut = DC_FILTER * (dcFilterLastOut + value - dcFilterLastIn);
            dcFilterLastIn = value;
            int sample = Math.clamp(Math.round(dcFilterLastOut), Short.MIN_VALUE, Short.MAX_VALUE);

            buffer[bufferPointer] = (byte) (sample & 0xff);
            buffer[bufferPointer + 1] = (byte) (sample >> 8);
            bufferPointer += SPEAKER_SAMPLESIZE;

            clock += clockStep;
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
