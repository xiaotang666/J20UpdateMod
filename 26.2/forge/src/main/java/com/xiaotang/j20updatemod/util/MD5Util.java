package com.xiaotang.j20updatemod.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** MD5 计算工具（对接汉化库 manifest.json 的 md5 字段）。 */
public final class MD5Util {

    private MD5Util() {
    }

    /** 计算文件 MD5，返回 32 位小写十六进制。 */
    public static String fileMd5Hex(Path file) throws IOException {
        MessageDigest digest = newDigest();
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                digest.update(buf, 0, n);
            }
        }
        return toHex(digest.digest());
    }

    /** 计算字节数组 MD5，返回 32 位小写十六进制。 */
    public static String bytesMd5Hex(byte[] data) {
        return toHex(newDigest().digest(data));
    }

    /** 不区分大小写比较两个 MD5 十六进制串。 */
    public static boolean matches(String expected, String actual) {
        return expected != null && actual != null && expected.equalsIgnoreCase(actual);
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 algorithm unavailable", e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
