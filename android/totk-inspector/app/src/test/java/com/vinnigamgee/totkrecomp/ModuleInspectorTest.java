package com.vinnigamgee.totkrecomp;

import java.io.ByteArrayInputStream;
import java.io.InterruptedIOException;
import java.security.MessageDigest;

/** No Android or third-party dependencies: javac/java run this in CI and locally. */
public final class ModuleInspectorTest {
    private static void require(boolean condition) { if (!condition) throw new AssertionError(); }
    private static void u32(byte[] b, int offset, int value) {
        for (int i = 0; i < 4; i++) b[offset + i] = (byte)(value >>> (8 * i));
    }
    private static ModuleInspector.Result inspect(byte[] data) throws Exception {
        return ModuleInspector.inspect("main", new ByteArrayInputStream(data),
            ModuleInspector.hex(MessageDigest.getInstance("SHA-256").digest(data)));
    }
    public static void main(String[] args) throws Exception {
        byte[] data = new byte[268];
        System.arraycopy(new byte[]{'N','S','O','0'}, 0, data, 0, 4);
        for (int i = 0; i < 3; i++) { u32(data, 0x10 + i * 16, 256 + i * 4); u32(data, 0x14 + i * 16, i * 4096); u32(data, 0x18 + i * 16, 4); }
        u32(data, 0x3c, 8);
        ModuleInspector.Result valid = inspect(data);
        require(valid.match && valid.imageSize == 8204 && valid.fileSize == 268);
        require(!ModuleInspector.inspect("main", new ByteArrayInputStream(data), "wrong-hash").match);
        require(!inspect(new byte[]{'N','S','O','0'}).match);
        u32(data, 0x10, 99999);
        require(!inspect(data).match);
        byte[] npdm = new byte[128];
        System.arraycopy(new byte[]{'M','E','T','A'}, 0, npdm, 0, 4);
        require(ModuleInspector.inspect("main.npdm", new ByteArrayInputStream(npdm),
            ModuleInspector.hex(MessageDigest.getInstance("SHA-256").digest(npdm))).match);
        Thread.currentThread().interrupt();
        try { inspect(data); throw new AssertionError("Cancellation was ignored"); }
        catch (InterruptedIOException expected) { Thread.interrupted(); }
        System.out.println("ModuleInspector: valid NSO, wrong hash, truncation, bounds, NPDM and cancellation passed");
    }
}
