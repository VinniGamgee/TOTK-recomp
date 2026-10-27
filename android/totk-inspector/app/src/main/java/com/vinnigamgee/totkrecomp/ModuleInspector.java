package com.vinnigamgee.totkrecomp;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/** Streaming ExeFS inspection. No game contents or paths are retained in a result. */
public final class ModuleInspector {
    public static final class Result {
        public String sha256;
        public String buildId;
        public long fileSize;
        public long imageSize;
        public long flags;
        public long[][] segments;
        public boolean match;
        public String error;
    }

    public static Result inspect(String name, InputStream input, String expected) throws IOException {
        Result r = new Result();
        MessageDigest digest;
        try { digest = MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException e) { throw new AssertionError(e); }
        byte[] header = new byte[256];
        int headerSize = 0;
        byte[] buffer = new byte[65536];
        for (int n; (n = input.read(buffer)) != -1;) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Análise cancelada");
            if (n == 0) continue;
            int copy = Math.min(n, header.length - headerSize);
            System.arraycopy(buffer, 0, header, headerSize, copy);
            headerSize += copy;
            digest.update(buffer, 0, n);
            r.fileSize += n;
        }
        r.sha256 = hex(digest.digest());
        r.match = r.sha256.equals(expected);
        if (name.equals("main.npdm")) {
            if (headerSize < 0x80 || !magic(header, "META")) r.error = "Cabeçalho NPDM inválido ou incompleto";
        } else {
            if (headerSize < 256 || !magic(header, "NSO0")) {
                r.error = "Cabeçalho NSO inválido ou incompleto";
            } else {
                r.buildId = hex(Arrays.copyOfRange(header, 0x40, 0x60));
                r.flags = u32(header, 0x0c);
                r.segments = new long[3][3];
                long previousEnd = 0;
                for (int i = 0; i < 3; i++) {
                    int off = 0x10 + i * 0x10;
                    long file = u32(header, off), memory = u32(header, off + 4), size = u32(header, off + 8);
                    long stored = (r.flags & (1L << i)) != 0 ? u32(header, 0x60 + i * 4) : size;
                    r.segments[i] = new long[]{memory, size, stored};
                    if (size > 0 && (file < 256 || file + stored > r.fileSize || memory < previousEnd))
                        r.error = "Segmento NSO fora do arquivo ou com sobreposição na memória";
                    previousEnd = memory + size;
                }
                r.imageSize = previousEnd + u32(header, 0x3c);
            }
        }
        if (r.error != null) r.match = false;
        return r;
    }

    private static boolean magic(byte[] bytes, String value) {
        for (int i = 0; i < value.length(); i++) if (bytes[i] != value.charAt(i)) return false;
        return true;
    }
    private static long u32(byte[] b, int i) {
        return (b[i] & 255L) | (b[i + 1] & 255L) << 8 | (b[i + 2] & 255L) << 16 | (b[i + 3] & 255L) << 24;
    }
    public static String hex(byte[] bytes) {
        StringBuilder s = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) s.append(Character.forDigit((b >>> 4) & 15, 16)).append(Character.forDigit(b & 15, 16));
        return s.toString();
    }
    private ModuleInspector() {}
}
