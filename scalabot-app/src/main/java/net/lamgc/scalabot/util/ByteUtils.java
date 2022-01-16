package net.lamgc.scalabot.util;

final class ByteUtils {

    private ByteUtils() {
    }

    public static String bytesToHexString(byte[] bytes) {
        StringBuilder builder = new StringBuilder();
        for (byte aByte : bytes) {
            builder.append(Integer.toHexString(aByte));
        }
        return builder.toString();
    }

}
