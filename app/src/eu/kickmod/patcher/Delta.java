package eu.kickmod.patcher;

import java.io.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.GZIPInputStream;

/** Strumieniowy format delta KDP1: zakresy z oryginalnego pliku i nowe bajty. */
public final class Delta {
    private Delta() {}

    public static void apply(File source, InputStream patch, File output,
                             long expectedSize, AtomicBoolean cancelled) throws IOException {
        try (RandomAccessFile original = new RandomAccessFile(source, "r");
             DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(patch)));
             OutputStream out = new BufferedOutputStream(new FileOutputStream(output))) {
            if (in.readInt() != 0x4b445031) throw new IOException("Invalid patch format.");
            long sourceSize = Integer.toUnsignedLong(in.readInt());
            long resultSize = Integer.toUnsignedLong(in.readInt());
            if (sourceSize != original.length() || resultSize != expectedSize || resultSize > 128L * 1024 * 1024)
                throw new IOException("Patch size mismatch.");
            byte[] buffer = new byte[65536];
            long written = 0;
            while (true) {
                check(cancelled);
                int opcode = in.readUnsignedByte();
                if (opcode == 0) break;
                long offset = opcode == 1 ? Integer.toUnsignedLong(in.readInt()) : 0;
                long length = Integer.toUnsignedLong(in.readInt());
                if ((opcode != 1 && opcode != 2) || length == 0 || length > resultSize - written)
                    throw new IOException("Invalid patch instruction.");
                if (opcode == 1) {
                    if (offset > sourceSize || length > sourceSize - offset) throw new IOException("Invalid source range.");
                    original.seek(offset);
                }
                long remaining = length;
                while (remaining > 0) {
                    check(cancelled);
                    int count = (int) Math.min(buffer.length, remaining);
                    if (opcode == 1) original.readFully(buffer, 0, count);
                    else in.readFully(buffer, 0, count);
                    out.write(buffer, 0, count);
                    remaining -= count;
                }
                written += length;
            }
            if (written != resultSize || in.read() != -1) throw new IOException("Incomplete or trailing patch data.");
        }
    }

    public static void check(AtomicBoolean cancelled) throws InterruptedIOException {
        if (cancelled.get() || Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Cancelled.");
    }
}
