package net.lamgc.scalabot.util;

final class ByteUtils {

    private ByteUtils() {
    }

    public static String bytesToHexString(byte[] bytes) {
        StringBuilder builder = new StringBuilder();
        for (byte aByte : bytes) {
            String hexBit = Integer.toHexString(aByte & 0xFF);
            builder.append(hexBit.length() == 1 ? "0" + hexBit : hexBit);
        }
        return builder.toString();
    }

}
