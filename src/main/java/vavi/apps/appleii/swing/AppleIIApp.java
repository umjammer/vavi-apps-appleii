/*
 * Copyright (c) 2008 by umjammer, All rights reserved.
 *
 * Programmed by umjammer
 *
 * Released under the GPL
 */

package vavi.apps.appleii.swing;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.IOException;
import java.io.InputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.zip.GZIPInputStream;
import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.event.MouseInputAdapter;
import javax.swing.event.MouseInputListener;

import vavi.apps.appleii.AppleDisplay;
import vavi.apps.appleii.AppleIIGo;
import vavi.apps.appleii.Paddle;

import static java.lang.System.getLogger;


/**
 * AppleIIApp.
 *
 * @author umjammer
 * @version 0.00 080912 umjammer initial version <br>
 */
public class AppleIIApp {

    private static final Logger logger = getLogger(AppleIIApp.class.getName());

    public static void main(String[] args) throws Exception {
        JFrame frame = new JFrame();
        frame.setTitle("AppleIIGo");
        MyView view = new MyView();
        frame.getContentPane().add(view);
        frame.pack();
        frame.setVisible(true);
        Thread thread = new Thread(view);
        thread.start();
    }

    /** */
    private static class MyView extends JComponent implements AppleIIGo.View, Runnable {

        private static final int MODE_INIT = 0;
        private static final int MODE_NORMAL = 1;
        private static final int MODE_DIRECT = 3;
        private static final int MODE_DISK1 = 4;
        private static final int MODE_DISK2 = 5;

        private int mode = MODE_INIT;

        private final AppleIIGo game;

        private NormalVC normalVC;
        private DirectVC directVC;
        private final DiskVC[] diskVCs = new DiskVC[2];
        private GameVC gameVC;

        /**
         * <pre>
         *  1
         *  2
         *  3
         *  4
         *  5   toggleStatMode
         *  6   toggleStepMode
         *  7   stepInstructions 1
         *  8   stepInstructions 128
         *  9   setVolume true
         *  0   setVolume false
         *  B   mode change
         *  R
         *  G   restart
         *  Y   reset
         *  O   pad button right
         *  #   pad button left
         *  U   pad up
         *  L   pad left
         *  R   pad right
         *  D   pad down
         * </pre>
         */
        class NormalVC {

            /** */
            void keyPressed(int keyCode) {
                logger.log(Level.TRACE, "NORMAL: " + keyCode);
                switch (keyCode) {
                    case KeyEvent.VK_1:     // 1
                        // user mapped ?
                        break;
                    case KeyEvent.VK_2:     // 2
                        // user mapped ?
                        break;
                    case KeyEvent.VK_3:     // 3
                        // user mapped ?
                        break;
                    case KeyEvent.VK_4:     // 4
                        // user mapped ?
                        break;
                    case KeyEvent.VK_5:     // 5
                        if (game.isCpuDebugEnabled()) {
                            game.toggleStatMode();
                        }
                        break;
                    case KeyEvent.VK_6:     // 6
                        if (game.isCpuDebugEnabled()) {
                            game.toggleStepMode();
                        }
                        break;
                    case KeyEvent.VK_7:     // 7
                        if (game.isCpuDebugEnabled()) {
                            game.stepInstructions(1);
                        }
                        break;
                    case KeyEvent.VK_8:     // 8
                        if (game.isCpuDebugEnabled()) {
                            game.stepInstructions(128);
                        }
                        break;
                    case KeyEvent.VK_9:     // 9
                        game.setVolume(true);
                        break;
                    case KeyEvent.VK_0:     // 0
                        game.setVolume(false);
                        break;
                    case 403:               // B
                        mode = MODE_DISK1;
                        logger.log(Level.TRACE, "mode: -> MODE_VIRTUAL_KEYBOARD");
                        break;
                    case 404:               // R
                        // user mapped ?
                        break;
                    case 405:               // G
                        game.restart();
                        break;
                    case 406:               // Y
                        game.reset();
                        break;
                    case KeyEvent.VK_ENTER: // O
                    case KeyEvent.VK_X: // for emulator
                        game.setButton(0, true);
                        break;
                    case 461:               // #
                    case KeyEvent.VK_Z: // for emulator
                        game.setButton(1, true);
                        break;
                    case KeyEvent.VK_LEFT:  // L
                        game.setPaddle(0, Paddle.PADDLE_LOW);
                        break;
                    case KeyEvent.VK_UP:    // U
                        game.setPaddle(1, Paddle.PADDLE_LOW);
                        break;
                    case KeyEvent.VK_DOWN:  // D
                        game.setPaddle(1, Paddle.PADDLE_HIGH);
                        break;
                    case KeyEvent.VK_RIGHT: // R
                        game.setPaddle(0, Paddle.PADDLE_HIGH);
                        break;
                }
            }

            /** */
            void keyReleased(int keyCode) {
                debug("KEY RELEASED: " + keyCode);
                switch (keyCode) {
                    case KeyEvent.VK_ENTER: // O
                    case KeyEvent.VK_X: // for emulator
                        game.setButton(0, false);
                        break;
                    case 461:               // #
                    case KeyEvent.VK_Z: // for emulator
                        game.setButton(1, false);
                        break;
                    case KeyEvent.VK_LEFT:  // L
                        game.setPaddle(0, Paddle.PADDLE_CENTER);
                        break;
                    case KeyEvent.VK_UP:    // U
                        game.setPaddle(1, Paddle.PADDLE_CENTER);
                        break;
                    case KeyEvent.VK_DOWN:  // D
                        game.setPaddle(1, Paddle.PADDLE_CENTER);
                        break;
                    case KeyEvent.VK_RIGHT: // R
                        game.setPaddle(0, Paddle.PADDLE_CENTER);
                        break;
                }
            }

            static final int Y = 384;

            /** */
            public void paint(Graphics2D g) {
                g.setColor(Color.black);
                g.drawRect(0, Y, 560, 540);
                if (debug) {
                    g.setColor(Color.white);
                    g.drawString(" Normal Mode: ", 0, Y + 36);
//                    g.drawString("   1: " + game.getDiskDriveResource(0), 0, Y + 36 * 2);
//                    g.drawString("   2: " + game.getDiskDriveResource(1), 0, Y + 36 * 3);
                }
            }
        }

        /**
         * <pre>
         *  B   mode change
         *  G   restart
         *  O   insert
         *  #   release
         *  U   disk previous
         *  D   disk next
         * </pre>
         */
        class DiskVC {

            final BufferedImage image;
            final int drive;
            final List<Path> files;

            DiskVC(int drive) throws IOException {
                this.drive = drive;
                this.image = ImageIO.read(AppleIIApp.class.getResource("/disk.png"));
                files = Files.list(root).filter(p -> p.getFileName().toString().toLowerCase().endsWith(".dsk") ||
                        p.getFileName().toString().toLowerCase().endsWith(".nib")).toList();
            }

            int fileIndex;

            void keyPressed(int keyCode) {
                logger.log(Level.TRACE, "DISK[" + drive + "]: " + keyCode);
                switch (keyCode) {
                    case KeyEvent.VK_UP:
                        if (fileIndex > 0) {
                            fileIndex--;
                        } else {
                            fileIndex = files.size() - 1;
                        }
                        name = files.get(fileIndex).getFileName().toString();
                        selectionMode = MODE_SELECTING;
                        break;
                    case KeyEvent.VK_DOWN:
                        if (fileIndex < files.size() - 1) {
                            fileIndex++;
                        } else {
                            fileIndex = 0;
                        }
                        name = files.get(fileIndex).getFileName().toString();
                        selectionMode = MODE_SELECTING;
                        break;
                    case KeyEvent.VK_ENTER:
                        name = files.get(fileIndex).getFileName().toString();
//                    game.mountDisk(drive, name);
                        selectionMode = MODE_SELECTED;
                        break;
                    case 461:               // #
                    case KeyEvent.VK_Z: // for emulator
                        name = null;
                        game.mountDisk(drive, null);
                        selectionMode = MODE_SELECTED;
                        break;
                    case 403:               // B
                        if (drive == 0) {
                            mode = MODE_DISK2;
                            diskVCs[1].init();
                            logger.log(Level.TRACE, "mode: -> DISK2");
                        } else {
                            if (debug) {
                                mode = MODE_DIRECT;
                                logger.log(Level.TRACE, "mode: -> DIRECT");
                            } else {
                                mode = MODE_NORMAL;
                                logger.log(Level.TRACE, "mode: -> NORMAL");
                            }
                        }
                        break;
                    case 405:               // G
                        game.restart();
                        break;
                    case 406:               // Y
                        game.reset();
                }
            }

            String name;
            static final int MODE_SELECTING = 1;
            static final int MODE_SELECTED = 0;
            int selectionMode;

            void init() {
                name = game.getDiskDriveResource(drive);
                logger.log(Level.TRACE, "DRIVE[" + drive + "]: " + name);
                selectionMode = MODE_SELECTED;
            }

            static final int Y = 384;

            /** */
            public void paint(Graphics2D g) {
                g.setColor(Color.black);
                g.drawRect(0, Y, 560, 540);
                g.drawImage(image, 0, Y, null);
                switch (selectionMode) {
                    case MODE_SELECTED:
                        g.setColor(Color.blue);
                        break;
                    case MODE_SELECTING:
                        g.setColor(Color.red);
                        break;
                }
                String label = (drive + 1) + ": " + (name == null || name.isEmpty() ? "NO DISK" : name.substring(0, name.length() - 4));
                g.drawString(label, 16, Y + 36);
            }
        }

        /** for emulator */
        class DirectVC {

            void keyPressed(int keyCode) {
                logger.log(Level.TRACE, "DIRECT: " + keyCode);
                switch (keyCode) {
                    case KeyEvent.VK_BACK_SPACE:
                    case KeyEvent.VK_LEFT:
                        game.setKeyLatch(8);
                        break;
                    case KeyEvent.VK_RIGHT:
                        game.setKeyLatch(21);
                        break;
                    case KeyEvent.VK_UP:
                        game.setKeyLatch(11);
                        break;
                    case KeyEvent.VK_DOWN:
                        game.setKeyLatch(10);
                        break;
                    case KeyEvent.VK_ESCAPE:
                        game.setKeyLatch(27);
                        break;
                    case KeyEvent.VK_DELETE:
                        game.setKeyLatch(127);
                        break;
                    case KeyEvent.VK_ENTER:
                        game.setKeyLatch(13);
                        break;
                    case 403:               // B
                        mode = MODE_NORMAL;
                        logger.log(Level.TRACE, "mode: -> NORMAL");
                        break;
                    case 405:               // G
                        game.restart();
                        break;
                    case 406:               // Y
                        game.reset();
                    default:
                        game.setKeyLatch(keyCode);
                        break;
                }
            }

            void mousePressed(MouseEvent e) {
                int modifiers = e.getModifiersEx();

                if (modifiers == KeyEvent.BUTTON1_DOWN_MASK) {
                    game.setButton(0, true);
                }
                if (modifiers == KeyEvent.BUTTON3_DOWN_MASK) {
                    game.setButton(1, true);
                }
            }

            void mouseReleased(MouseEvent e) {
                int modifiers = e.getModifiersEx();

                if (modifiers == KeyEvent.BUTTON1_DOWN_MASK) {
                    game.setButton(0, false);
                }
                if (modifiers == KeyEvent.BUTTON3_DOWN_MASK) {
                    game.setButton(1, false);
                }
            }

            void mouseMoved(MouseEvent e) {
                game.setPaddlePos(e.getX(), e.getY());
            }

            static final int Y = 384;

            /* */
            public void paint(Graphics2D g) {
                g.setColor(Color.black);
                g.drawRect(0, Y, 560, 540);
                g.setColor(Color.white);
                g.drawString(" Direct Mode", 0, Y + 36);
            }
        }

        private KeyListener keyListener = new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent event) {
                int keyCode = event.getKeyCode();
                switch (mode) {
                    case MODE_NORMAL:
                        normalVC.keyPressed(keyCode);
                        break;
                    case MODE_DIRECT:
                        directVC.keyPressed(keyCode);
                        break;
                    case MODE_DISK1:
                        diskVCs[0].keyPressed(keyCode);
                        break;
                    case MODE_DISK2:
                        diskVCs[1].keyPressed(keyCode);
                        break;
                }
            }

            @Override
            public void keyReleased(KeyEvent event) {
                int keyCode = event.getKeyCode();
                switch (mode) {
                    case MODE_NORMAL:
                        normalVC.keyReleased(keyCode);
                        break;
                }
            }
        };

        MouseInputListener mouseInputListener = new MouseInputAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                switch (mode) {
                    case MODE_DIRECT:
                        directVC.mousePressed(event);
                        break;
                }
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                switch (mode) {
                    case MODE_DIRECT:
                        directVC.mouseReleased(event);
                        break;
                }
            }

            @Override
            public void mouseMoved(MouseEvent event) {
                switch (mode) {
                    case MODE_DIRECT:
                        directVC.mouseMoved(event);
                        break;
                }
            }
        };

        @Override
        public void debug(String s) {
            logger.log(Level.INFO, s);
        }

        @Override
        public void debug(Throwable t) {
            logger.log(Level.ERROR, t.getMessage(), t);
        }

        /** */
        private final boolean debug;

        final BufferedImage charSetSource;
        final Path root;

        MyView() throws IOException {
            root = Path.of(System.getProperty("user.home"), ".config/appleiigo");

            this.game = new AppleIIGo();
            game.setView(this);
            AppleIIGo.Dao dao = new MyDao();
            game.setDao(dao);

            //
            charSetSource = ImageIO.read(AppleIIApp.class.getResource("/character_set.png"));

            debug = "true".equals(dao.getParameter("debugMode"));
            logger.log(Level.DEBUG, "debug: " + debug);

            //
            addKeyListener(keyListener);
            addMouseListener(mouseInputListener);
            addMouseMotionListener(mouseInputListener);

            setPreferredSize(new Dimension(AppleDisplay.DISPLAY_SIZE_X, AppleDisplay.DISPLAY_SIZE_Y));
            setFocusable(true);
        }

        @Override
        public void run() {
            try {
                game.init();

                this.directVC = new DirectVC();
                this.normalVC = new NormalVC();
                this.diskVCs[0] = new DiskVC(0);
                this.diskVCs[1] = new DiskVC(1);
                this.gameVC = new GameVC();

                mode = MODE_NORMAL;
                logger.log(Level.TRACE, "mode: -> MODE_NORMAL");

                game.start();
            } catch (Throwable t) {
                debug(t);
            }
        }

        class GameVC {

            int displayScaledSizeX;
            int displayScaledSizeY;
            final BufferedImage displayImage;
            final BufferedImage displayImagePaused;
            final BufferedImage displayImageGlare;

            GameVC() throws IOException {
                // Load glare and pause images
                displayImage = new BufferedImage(AppleDisplay.DISPLAY_SIZE_X, AppleDisplay.DISPLAY_SIZE_Y, BufferedImage.TYPE_INT_ARGB);
                displayImageGlare = ImageIO.read(AppleIIApp.class.getResource("/glare.png"));
                displayImagePaused = ImageIO.read(AppleIIApp.class.getResource("/paused.png"));
            }

            /** */
            void paint(Graphics2D g) {

                if (displayImage != null) {
                    ((DataBufferInt) displayImage.getRaster().getDataBuffer()).setPixels(0, 0, AppleDisplay.DISPLAY_SIZE_X, AppleDisplay.DISPLAY_SIZE_Y, game.getDisplayImageBuffer());

                    g.drawImage(displayImage,
                            0, 0, AppleDisplay.DISPLAY_SIZE_X, AppleDisplay.DISPLAY_SIZE_Y,
                            0, 0, displayScaledSizeX, displayScaledSizeY,
                            null);
                }

                if (game.isStatMode()) {
                    g.setColor(Color.black);
                    g.drawRect(displayScaledSizeX, 0, displayScaledSizeX + 640, 480);
                    drawStatInfo(g);
                }

                if ((displayImagePaused != null) && game.isPaused()) {
                    g.drawImage(displayImagePaused,
                            0, 0, AppleDisplay.DISPLAY_SIZE_X, AppleDisplay.DISPLAY_SIZE_Y,
                            0, 0, displayScaledSizeX, displayScaledSizeY,
                            null);
                }

                if (game.isGlare() && (displayImageGlare != null)) {
                    g.drawImage(displayImageGlare,
                            0, 0, AppleDisplay.DISPLAY_SIZE_X, AppleDisplay.DISPLAY_SIZE_Y,
                            0, 0, displayScaledSizeX, displayScaledSizeY,
                            null);
                }
            }

            /**
             * Paint stat info
             */
            private void drawStatInfo(Graphics2D g) {

                StringTokenizer lines = new StringTokenizer(game.getStatInfo() + "\n", "\n");

                final int fontSize = 32;
                int drawPosY = fontSize;

                g.setColor(Color.white);

                while (lines.hasMoreTokens()) {
                    String line = lines.nextToken();
                    g.drawString(line, displayScaledSizeX, drawPosY);
                    drawPosY += fontSize;
                }
            }
        }

        @Override
        public void update(Graphics g) {
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (gameVC == null) {
                return;
            }
            Graphics2D g2d = (Graphics2D) g;
            gameVC.paint(g2d);
            switch (mode) {
                case MODE_NORMAL:
                    normalVC.paint(g2d);
                    break;
                case MODE_DIRECT:
                    directVC.paint(g2d);
                    break;
                case MODE_DISK1:
                    diskVCs[0].paint(g2d);
                    break;
                case MODE_DISK2:
                    diskVCs[1].paint(g2d);
                    break;
            }
        }

        @Override
        public void getCharSet(int[] buffer, int w, int h, int s) {
            charSetSource.getRGB(
                    0, 0,
                    w, h,
                    buffer,
                    0, s);
        }

        @Override
        public void setDisplayScaledSizeX(int w) {
            gameVC.displayScaledSizeX = w;
        }

        @Override
        public void setDisplayScaledSizeY(int h) {
            gameVC.displayScaledSizeY = h;
        }

        class MyDao implements AppleIIGo.Dao {

            final Properties props = new Properties();

            MyDao() throws IOException {
                props.load(Files.newInputStream(root.resolve("appleii.properties")));
            }

            @Override
            public String getParameter(String parameter) {
                return props.getProperty(parameter);
            }

            @Override
            public void read(byte[] bytes, int offset, int length) {
                try {
                    int l = 0;
                    while (l < length) {
                        int r = is.read(bytes, offset + l, length - l);
                        if (r < 0) {
                            logger.log(Level.TRACE, "Illegal EOF: " + l + "/" + length);
                            break;
                        }
                        l += r;
                    }
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            }

            private InputStream is = null;

            @Override
            public void openInputStream(String resource) {
                try {
                    this.is = Files.newInputStream(root.resolve(resource));

                    if (resource.toLowerCase().endsWith(".gz")) {
                        this.is = new GZIPInputStream(is);
                    }
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }

            @Override
            public void closeInputStream() {
                try {
                    is.close();
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            }

//            private OutputStream os;

            /**
             * Open output stream
             */
//            public void openOutputStream(String resource) throws IOException {
//                if (!(resource.substring(0, 6).equals("http://"))) {
//                    this.os = new FileOutputStream(resource);
//                }
//            }
        }
    }
}
