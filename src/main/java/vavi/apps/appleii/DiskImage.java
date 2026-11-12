/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.apps.appleii;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.zip.GZIPInputStream;

import com.webcodepro.applecommander.storage.DiskConstants;
import com.webcodepro.applecommander.storage.DiskFactory;
import com.webcodepro.applecommander.storage.Disks;
import com.webcodepro.applecommander.storage.FormattedDisk;
import org.applecommander.device.BlockDevice;
import org.applecommander.device.BlockToTrackSectorAdapter;
import org.applecommander.device.OrderedTrackSectorDevice;
import org.applecommander.device.ProdosBlockToTrackSectorAdapterStrategy;
import org.applecommander.device.ProdosOrderedBlockDevice;
import org.applecommander.device.SectorOrder;
import org.applecommander.device.SkewedTrackSectorDevice;
import org.applecommander.device.TrackSectorDevice;
import org.applecommander.device.nibble.NibbleTrackReaderWriter;
import org.applecommander.hint.Hint;
import org.applecommander.image.UniversalDiskImage;
import org.applecommander.image.WozImage;
import org.applecommander.source.DataBufferSource;
import org.applecommander.source.Source;
import org.applecommander.source.Sources;
import org.applecommander.util.DataBuffer;

import static java.lang.System.getLogger;


/**
 * A 5.25" disk image converted into raw nibble tracks for the {@link DiskII} emulation.
 * <p>
 * Image container recognition is delegated to AppleCommander, so following formats are supported.
 * <ul>
 * <li>sector images: .dsk, .do, .po (sector order of .dsk is detected by the file system)</li>
 * <li>nibble images: .nib</li>
 * <li>bitstream images: .woz (WOZ1, WOZ2; whole tracks only, bit timing is dropped)</li>
 * <li>wrappers: .2mg/.2img (DOS, ProDOS, nibble), gzip compressed (.gz) of any above</li>
 * </ul>
 * 13 sector images and images larger than a 5.25" disk (800K, hard disk) are not supported.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-11-12 nsano initial version <br>
 */
public final class DiskImage {

    private static final Logger logger = getLogger(DiskImage.class.getName());

    /** tracks the emulator handles */
    public static final int NUM_TRACKS = 35;
    /** 0x1A00 (6656) for .NIB (was 6250) */
    public static final int RAW_TRACK_BYTES = 0x1A00;

    private static final int TRACK_BYTES = TrackSectorDevice.SECTOR_SIZE * Nibblizer.NUM_SECTORS;

    /** nibble tracks, the length of each track may differ (WOZ) */
    private final byte[][] tracks;
    private final boolean writeProtected;
    private final String format;

    private DiskImage(byte[][] tracks, boolean writeProtected, String format) {
        this.tracks = tracks;
        this.writeProtected = writeProtected;
        this.format = format;
    }

    /** nibble tracks, the length of each track may differ */
    public byte[][] getTracks() {
        return tracks;
    }

    /** write-protection, given one or the one specified by the image */
    public boolean isWriteProtected() {
        return writeProtected;
    }

    /** a description of the image format for logging */
    public String getFormat() {
        return format;
    }

    /** disk image extensions this class can read, also gzip compressed ones (".gz") */
    public static final List<String> EXTENSIONS = List.of(".dsk", ".do", ".po", ".nib", ".woz", ".2mg", ".2img");

    /** @return true when the filename has one of {@link #EXTENSIONS} */
    public static boolean isDiskImage(String filename) {
        String name = filename.toLowerCase();
        if (name.endsWith(".gz")) {
            name = name.substring(0, name.length() - 3);
        }
        return EXTENSIONS.stream().anyMatch(name::endsWith);
    }

    /** no disk in the drive */
    public static DiskImage empty() {
        byte[][] tracks = new byte[NUM_TRACKS][RAW_TRACK_BYTES];
        return new DiskImage(tracks, false, "empty");
    }

    /**
     * Reads a disk image.
     *
     * @param data whole image bytes (gzip compressed bytes are also accepted)
     * @param name file name, the extension is used as a hint
     * @param writeProtected initial write-protection, may be overridden by the image
     * @param volumeNumber DOS volume number for sector images, may be overridden by the image
     * @throws IllegalArgumentException the image is not supported
     */
    public static DiskImage read(byte[] data, String name, boolean writeProtected, int volumeNumber) {
        data = gunzipIfNeeded(data);

        Source source = DataBufferSource.create(data, name).get();
        // unwrap 2IMG, DiskCopy etc.
        source = Sources.create(source).orElse(source);

        // Container#get() does not answer the container itself
        var udi = source instanceof UniversalDiskImage u ? Optional.of(u) : source.get(UniversalDiskImage.class);
        if (udi.isPresent()) {
            UniversalDiskImage.Info info = udi.get().getInfo();
            if (info.isLocked()) {
                writeProtected = true; // only override if set
            }
            // 2IMG spec: bit 8 means the low 8 bits contain the DOS volume number
            if ((info.flags() & 0x100) != 0) {
                volumeNumber = info.flags() & 0xff;
            }
        }

        DiskFactory.Context ctx = new DiskFactory.Context(source);
        if (ctx.nibbleTrackReaderWriter != null) {
            return readNibbles(source, ctx.nibbleTrackReaderWriter, writeProtected);
        } else {
            return readSectors(source, writeProtected, volumeNumber);
        }
    }

    /** nibble, woz */
    private static DiskImage readNibbles(Source source, NibbleTrackReaderWriter nibbles, boolean writeProtected) {
        String format;
        if (nibbles instanceof WozImage) {
            format = "woz";
            writeProtected |= isWozWriteProtected(source);
        } else {
            format = "nibble";
        }

        int tracksOnDevice = nibbles.getTracksOnDevice();
        if (tracksOnDevice > NUM_TRACKS) {
            logger.log(Level.DEBUG, "tracks: " + tracksOnDevice + ", only " + NUM_TRACKS + " tracks are used");
        }
        byte[][] tracks = new byte[NUM_TRACKS][];
        for (int t = 0; t < NUM_TRACKS; t++) {
            byte[] track = null;
            if (t < tracksOnDevice) {
                try {
                    track = nibbles.readTrackData(t).asBytes();
                } catch (RuntimeException e) {
                    logger.log(Level.DEBUG, "track " + t + ": " + e);
                }
            }
            if (track == null || track.length == 0) {
                // unformatted track
                track = new byte[RAW_TRACK_BYTES];
                Arrays.fill(track, (byte) 0xff);
            }
            tracks[t] = track;
        }
        return new DiskImage(tracks, writeProtected, format);
    }

    /** dsk, do, po */
    private static DiskImage readSectors(Source source, boolean writeProtected, int volumeNumber) {
        if (source.isApproxEQ(DiskConstants.APPLE_13SECTOR_DISK)) {
            throw new IllegalArgumentException("13 sector images are not supported: " + source.getName());
        }
        if (!source.isApproxBetween(DiskConstants.APPLE_140KB_DISK, DiskConstants.APPLE_160KB_DISK)) {
            throw new IllegalArgumentException("not a 5.25\" disk image: " + source.getName() + ", size: " + source.getSize());
        }

        SectorOrder order = detectSectorOrder(source);
        TrackSectorDevice dosOrdered = switch (order) {
            case PRODOS -> new BlockToTrackSectorAdapter(
                    new ProdosOrderedBlockDevice(source, BlockDevice.STANDARD_BLOCK_SIZE),
                    new ProdosBlockToTrackSectorAdapterStrategy());
            default -> new OrderedTrackSectorDevice(source, SectorOrder.DOS);
        };
        TrackSectorDevice physical = SkewedTrackSectorDevice.dosToPhysicalSkew(dosOrdered);
        int tracksOnDevice = Math.min(NUM_TRACKS, physical.getGeometry().tracksOnDisk());

        Nibblizer nibblizer = new Nibblizer();
        byte[][] tracks = new byte[NUM_TRACKS][RAW_TRACK_BYTES];
        byte[] track = new byte[TRACK_BYTES];
        for (int t = 0; t < tracksOnDevice; t++) {
            DataBuffer buffer = DataBuffer.wrap(track);
            for (int s = 0; s < Nibblizer.NUM_SECTORS; s++) {
                physical.readSector(t, s, s * TrackSectorDevice.SECTOR_SIZE, buffer);
            }
            nibblizer.trackToNibbles(track, tracks[t], volumeNumber, t);
        }
        return new DiskImage(tracks, writeProtected, order.name().toLowerCase() + " order");
    }

    /**
     * Determines the sector order of the image bytes.
     * The hint from the wrapper or the extension takes precedence,
     * otherwise file systems on the image tell the order.
     */
    static SectorOrder detectSectorOrder(Source source) {
        if (source.is(Hint.DOS_SECTOR_ORDER) || source.extensionLike("do")) {
            return SectorOrder.DOS;
        }
        if (source.is(Hint.PRODOS_BLOCK_ORDER) || source.extensionLike("po")) {
            return SectorOrder.PRODOS;
        }
        try {
            for (FormattedDisk disk : Disks.inspect(source).disks) {
                if (disk.get(ProdosOrderedBlockDevice.class).isPresent()) {
                    logger.log(Level.DEBUG, "detected: " + disk.getFormat() + ", prodos order");
                    return SectorOrder.PRODOS;
                }
                if (disk.get(OrderedTrackSectorDevice.class).isPresent()) {
                    logger.log(Level.DEBUG, "detected: " + disk.getFormat() + ", dos order");
                    return SectorOrder.DOS;
                }
            }
        } catch (RuntimeException e) {
            logger.log(Level.DEBUG, "inspect: " + e);
        }
        // unknown file system (e.g. games w/ custom DOS), assume dos order
        return SectorOrder.DOS;
    }

    /** reads the write protected flag in the INFO chunk */
    private static boolean isWozWriteProtected(Source source) {
        DataBuffer header = source.readBytes(0, 23);
        // "WOZx" FF 0A 0D 0A, CRC32, then the first chunk which must be INFO
        if (header.limit() >= 23 && header.getInt(12) == WozImage.INFO_CHUNK_ID) {
            return header.getUnsignedByte(20 + 2) == 1;
        }
        return false;
    }

    private static byte[] gunzipIfNeeded(byte[] data) {
        if (data.length > 2 && (data[0] & 0xff) == 0x1f && (data[1] & 0xff) == 0x8b) {
            try (GZIPInputStream is = new GZIPInputStream(new ByteArrayInputStream(data))) {
                return is.readAllBytes();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return data;
    }
}
