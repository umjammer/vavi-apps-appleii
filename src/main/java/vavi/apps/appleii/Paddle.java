/*
 * AppleIIGo
 * Apple II Emulator for J2ME
 * (C) 2006 by Marc S. Ressl(ressl@lonetree.com)
 * Released under the GPL
 */

package vavi.apps.appleii;

import java.util.function.Supplier;


public class Paddle {

    // Public variables
    public static final int PADDLE_LOW = 0;
    public static final int PADDLE_CENTER = 127;
    public static final int PADDLE_HIGH = 255;

    public static final int PADDLEMODE_DIRECT = 0;
    public static final int PADDLEMODE_FILTERED = 1;

    /** Instances of other classes */
    private final Supplier<Integer> clock;

    /** Button variables */
    private final int[] buttonRegister = new int[4];

    /** Paddle variables */
//	  private int paddleMode;

    private final int[] paddleClockEvent = new int[4];
    private final int[] paddleClockInc = new int[4];

    /**
     * Paddle class constructor
     *
     * @param clock The EmAppleII clock supplier
     */
    public Paddle(Supplier<Integer> clock) {
        this.clock = clock;

        setPaddlePos(0, PADDLE_CENTER);
        setPaddlePos(1, PADDLE_CENTER);
        setPaddlePos(2, PADDLE_CENTER);
        setPaddlePos(3, PADDLE_CENTER);
    }

    /**
     * Set button state
     *
     * @param pressed State
     * @param button  Paddle button
     */
    public void setButton(int button, boolean pressed) {
        buttonRegister[button] = (pressed ? 0x80 : 0x00);
    }

    /**
     * Button register
     *
     * @param button Paddle button
     */
    public int getButtonRegister(int button) {
        return buttonRegister[button];
    }

    /**
     * Set paddle position
     *
     * @param paddle Address
     * @param value  Value
     */
    public void setPaddlePos(int paddle, int value) {
        /*
         * Magic formula, see ROM $FB1E-$FB2E,
         * We calculate the numbers of cycles after which
         * the RC circuit of a triggered paddle will discharge.
         */
        paddleClockInc[paddle] = value * 11 + 8;
    }

    /**
     * Trigger paddle register
     */
    public void triggerRegister() {
        paddleClockEvent[0] = clock.get() + paddleClockInc[0];
        paddleClockEvent[1] = clock.get() + paddleClockInc[1];
        paddleClockEvent[2] = clock.get() + paddleClockInc[2];
        paddleClockEvent[3] = clock.get() + paddleClockInc[3];
    }

    /**
     * Get paddle register
     *
     * @param paddle Address
     */
    public int getPaddleRegister(int paddle) {
        return ((((paddleClockEvent[paddle] - clock.get()) & 0x7fff_ffff) < 0x4000_0000) ? 0x80 : 0x00);
    }
}
