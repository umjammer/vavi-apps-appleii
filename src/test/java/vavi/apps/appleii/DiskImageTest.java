/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.apps.appleii;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Random;
import java.util.zip.GZIPOutputStream;

import com.webcodepro.applecommander.storage.os.dos33.DosFormatDisk;
import com.webcodepro.applecommander.storage.os.prodos.ProdosFormatDisk;
import org.applecommander.device.BlockDevice;
import org.applecommander.device.OrderedTrackSectorDevice;
import org.applecommander.device.ProdosBlockToTrackSectorAdapterStrategy;
import org.applecommander.device.ProdosOrderedBlockDevice;
import org.applecommander.device.SectorOrder;
import org.applecommander.device.SkewedTrackSectorDevice;
import org.applecommander.device.TrackSectorDevice;
import org.applecommander.device.TrackSectorNibbleDevice;
import org.applecommander.image.NibbleImage;
import org.applecommander.source.DataBufferSource;
import org.applecommander.source.Source;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * DiskImageTest.
 * <p>
 * All images are synthesized from one random DOS ordered image,
 * every container format must result the same nibble tracks.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-11-12 nsano initial version <br>
 */
class DiskImageTest {

    static final int TRACKS = 35;
    static final int TRACK_BYTES = 4096;
    static final int DISK_BYTES = TRACKS * TRACK_BYTES;
    static final int VOLUME = 254;

    /** physical sector to DOS 3.3 logical sector (the table the original DiskII used) */
    static final int[] DOS_SKEW = {0x0, 0x7, 0xe, 0x6, 0xd, 0x5, 0xc, 0x4, 0xb, 0x3, 0xa, 0x2, 0x9, 0x1, 0x8, 0xf};

    static byte[] dsk;
    static byte[] po;

    @BeforeAll
    static void setup() {
        dsk = new byte[DISK_BYTES];
        new Random(6502).nextBytes(dsk);
        po = dosToProdosOrder(dsk);
    }

    /** reorders DOS ordered bytes into ProDOS block ordered bytes */
    static byte[] dosToProdosOrder(byte[] d) {
        byte[] p = new byte[d.length];
        for (int t = 0; t < TRACKS; t++) {
            for (int s = 0; s < 16; s++) {
                int block = t * 8 + ProdosBlockToTrackSectorAdapterStrategy.blockInterleave[s];
                int offset = block * 512 + ProdosBlockToTrackSectorAdapterStrategy.blockOffsets[s] * 256;
                System.arraycopy(d, t * TRACK_BYTES + s * 256, p, offset, 256);
            }
        }
        return p;
    }

    /** nibble tracks made the same way as the original DiskII#readDisk */
    static byte[][] expected(byte[] dosOrdered, int volume) {
        Nibblizer nibblizer = new Nibblizer();
        byte[][] tracks = new byte[TRACKS][DiskImage.RAW_TRACK_BYTES];
        byte[] physical = new byte[TRACK_BYTES];
        for (int t = 0; t < TRACKS; t++) {
            for (int p = 0; p < 16; p++) {
                System.arraycopy(dosOrdered, t * TRACK_BYTES + DOS_SKEW[p] * 256, physical, p * 256, 256);
            }
            nibblizer.trackToNibbles(physical, tracks[t], volume, t);
        }
        return tracks;
    }

    static byte[] concat(byte[][] tracks) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        for (byte[] track : tracks) baos.writeBytes(track);
        return baos.toByteArray();
    }

    static void assertTracks(byte[][] expected, DiskImage actual) {
        assertEquals(expected.length, actual.getTracks().length);
        for (int t = 0; t < expected.length; t++) {
            assertArrayEquals(expected[t], actual.getTracks()[t], "track " + t);
        }
    }

    @Test
    void testDsk() {
        DiskImage image = DiskImage.read(dsk, "random.dsk", false, VOLUME);
        assertTracks(expected(dsk, VOLUME), image);
        assertFalse(image.isWriteProtected());
    }

    @Test
    void testDo() {
        assertTracks(expected(dsk, VOLUME), DiskImage.read(dsk, "random.do", false, VOLUME));
    }

    @Test
    void testPo() {
        assertTracks(expected(dsk, VOLUME), DiskImage.read(po, "random.po", false, VOLUME));
    }

    @Test
    void testGzip() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream os = new GZIPOutputStream(baos)) {
            os.write(po);
        }
        assertTracks(expected(dsk, VOLUME), DiskImage.read(baos.toByteArray(), "random.po.gz", false, VOLUME));
    }

    /** 2IMG header */
    static byte[] twoImg(int format, int flags, byte[] data) {
        ByteBuffer bb = ByteBuffer.allocate(64 + data.length).order(ByteOrder.LITTLE_ENDIAN);
        bb.put("2IMG".getBytes()).put("TEST".getBytes());
        bb.putShort((short) 64).putShort((short) 1);
        bb.putInt(format).putInt(flags).putInt(format == 1 ? 280 : 0);
        bb.putInt(64).putInt(data.length);
        bb.position(64);
        bb.put(data);
        return bb.array();
    }

    @Test
    void test2mgProdos() {
        int flags = 0x8000_0000 | 0x100 | 10; // locked, volume 10
        DiskImage image = DiskImage.read(twoImg(1, flags, po), "random.2mg", false, VOLUME);
        assertTracks(expected(dsk, 10), image);
        assertTrue(image.isWriteProtected());
    }

    @Test
    void test2mgDos() {
        // even if the name says ".po", the 2IMG header wins
        DiskImage image = DiskImage.read(twoImg(0, 0, dsk), "random.po.2mg", false, VOLUME);
        assertTracks(expected(dsk, VOLUME), image);
        assertFalse(image.isWriteProtected());
    }

    @Test
    void testNib() {
        byte[][] expected = expected(dsk, VOLUME);
        DiskImage image = DiskImage.read(concat(expected), "random.nib", false, VOLUME);
        assertTracks(expected, image);
        assertEquals("nibble", image.getFormat());
    }

    @Test
    void test2mgNib() {
        byte[][] expected = expected(dsk, VOLUME);
        assertTracks(expected, DiskImage.read(twoImg(2, 0, concat(expected)), "random.2mg", false, VOLUME));
    }

    /** builds a minimal WOZ2 image, a nibble is written as 8 bits */
    static byte[] woz2(byte[][] tracks, boolean writeProtected) {
        int blocksPerTrack = (DiskImage.RAW_TRACK_BYTES + 511) / 512;
        int trksSize = 160 * 8 + TRACKS * blocksPerTrack * 512;
        ByteBuffer bb = ByteBuffer.allocate(256 + trksSize).order(ByteOrder.LITTLE_ENDIAN);
        bb.put("WOZ2".getBytes()).put(new byte[] {(byte) 0xff, 0x0a, 0x0d, 0x0a}).putInt(0);
        // INFO
        bb.put("INFO".getBytes()).putInt(60);
        int info = bb.position();
        bb.put((byte) 2).put((byte) 1).put((byte) (writeProtected ? 1 : 0)).put((byte) 0).put((byte) 1);
        bb.put(Arrays.copyOf("vavi".getBytes(), 32));
        bb.put((byte) 1).put((byte) 0).put((byte) 32).putShort((short) 0).putShort((short) 0).putShort((short) blocksPerTrack);
        bb.position(info + 60);
        // TMAP
        bb.put("TMAP".getBytes()).putInt(160);
        byte[] tmap = new byte[160];
        Arrays.fill(tmap, (byte) 0xff);
        for (int t = 0; t < TRACKS; t++) tmap[t * 4] = (byte) t;
        bb.put(tmap);
        // TRKS
        bb.put("TRKS".getBytes()).putInt(trksSize);
        assertEquals(256, bb.position());
        for (int t = 0; t < 160; t++) {
            if (t < TRACKS) {
                bb.putShort((short) (3 + t * blocksPerTrack)).putShort((short) blocksPerTrack).putInt(tracks[t].length * 8);
            } else {
                bb.putShort((short) 0).putShort((short) 0).putInt(0);
            }
        }
        for (int t = 0; t < TRACKS; t++) {
            bb.position((3 + t * blocksPerTrack) * 512);
            bb.put(tracks[t]);
        }
        return bb.array();
    }

    @Test
    void testWoz() {
        // 0x7f padding is not a valid disk byte in a bitstream, real tracks are filled w/ sync bytes
        byte[][] expected = expected(dsk, VOLUME);
        for (byte[] track : expected) {
            for (int i = 0; i < track.length; i++) {
                if (track[i] == Nibblizer.PAD_NIBBLE) track[i] = (byte) 0xff;
            }
        }
        DiskImage image = DiskImage.read(woz2(expected, true), "random.woz", false, VOLUME);
        assertTracks(expected, image);
        assertEquals("woz", image.getFormat());
        assertTrue(image.isWriteProtected());

        assertFalse(DiskImage.read(woz2(expected, false), "random.woz", false, VOLUME).isWriteProtected());
    }

    /** decodes our nibble tracks with AppleCommander's own nibble reader */
    @Test
    void testNibblesDecodable() {
        DiskImage image = DiskImage.read(dsk, "random.dsk", false, VOLUME);
        Source source = DataBufferSource.create(concat(image.getTracks()), "x.nib").get();
        TrackSectorDevice device = TrackSectorNibbleDevice.identify(new NibbleImage(source)).orElseThrow();
        TrackSectorDevice dos = SkewedTrackSectorDevice.physicalToDosSkew(device);
        for (int t = 0; t < TRACKS; t++) {
            for (int s = 0; s < 16; s++) {
                byte[] sector = dos.readSector(t, s).asBytes();
                assertArrayEquals(Arrays.copyOfRange(dsk, t * TRACK_BYTES + s * 256, t * TRACK_BYTES + s * 256 + 256), sector,
                        "T" + t + "S" + s);
            }
        }
    }

    /** a ProDOS volume in ProDOS order but named ".dsk" */
    @Test
    void testDetectProdosOrderedDsk() {
        Source source = DataBufferSource.create(DISK_BYTES, "prodos.po").get();
        ProdosFormatDisk.create("prodos.po", "TEST", new ProdosOrderedBlockDevice(source, BlockDevice.STANDARD_BLOCK_SIZE));
        byte[] bytes = source.readAllBytes().asBytes();

        Source dsk = DataBufferSource.create(bytes, "prodos.dsk").get();
        assertEquals(SectorOrder.PRODOS, DiskImage.detectSectorOrder(dsk));

        byte[][] asPo = DiskImage.read(bytes, "prodos.po", false, VOLUME).getTracks();
        DiskImage image = DiskImage.read(bytes, "prodos.dsk", false, VOLUME);
        assertTracks(asPo, image);
        // reading it as DOS order is wrong
        assertNotEquals(Arrays.deepHashCode(asPo), Arrays.deepHashCode(DiskImage.read(bytes, "prodos.do", false, VOLUME).getTracks()));
    }

    /** a DOS 3.3 volume in DOS order named ".dsk" */
    @Test
    void testDetectDosOrderedDsk() {
        Source source = DataBufferSource.create(DISK_BYTES, "dos.do").get();
        DosFormatDisk.create("dos.do", new OrderedTrackSectorDevice(source, SectorOrder.DOS));
        byte[] bytes = source.readAllBytes().asBytes();

        Source dsk = DataBufferSource.create(bytes, "dos.dsk").get();
        assertEquals(SectorOrder.DOS, DiskImage.detectSectorOrder(dsk));
        assertTracks(DiskImage.read(bytes, "dos.do", false, VOLUME).getTracks(), DiskImage.read(bytes, "dos.dsk", false, VOLUME));
    }

    @Test
    void testUnsupported() {
        assertThrows(IllegalArgumentException.class, () -> DiskImage.read(new byte[116480], "dos32.d13", false, VOLUME));
        assertThrows(IllegalArgumentException.class, () -> DiskImage.read(new byte[819200], "800k.po", false, VOLUME));
        assertThrows(IllegalArgumentException.class, () -> DiskImage.read(new byte[1000], "small.dsk", false, VOLUME));
    }

    /** loads real disk images in the user's config directory */
    @Test
    @EnabledIfSystemProperty(named = "vavi.test", matches = "ide")
    void testRealDisks() throws IOException {
        Path root = Path.of(System.getProperty("user.home"), ".config/appleiigo/disks");
        try (var paths = Files.walk(root, java.nio.file.FileVisitOption.FOLLOW_LINKS)) {
            for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
                String name = path.getFileName().toString();
                if (!DiskImage.isDiskImage(name)) continue;
                DiskImage image;
                try {
                    image = DiskImage.read(Files.readAllBytes(path), name, false, VOLUME);
                } catch (IllegalArgumentException e) {
                    System.out.printf("%-48s unsupported: %s%n", name, e.getMessage());
                    continue;
                }
                Source source = DataBufferSource.create(concat(image.getTracks()), "x.nib").get();
                // standard disks must be decodable as 16 sector disks, copy protected ones may not
                boolean decodable = image.getTracks()[0].length == DiskImage.RAW_TRACK_BYTES
                        && TrackSectorNibbleDevice.identify(new NibbleImage(source)).isPresent();
                System.out.printf("%-48s %-12s decodable: %s%n", name, image.getFormat(), decodable);
            }
        }
    }
}
