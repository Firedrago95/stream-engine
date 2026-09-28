package io.slice.stream.engine.analyzer.domain.similarity;

public final class ChatterHashUtils {

    private static final int HASH_LENGTH = 16;
    private static final int RADIX = 16;
    private static final String ANONYMOUS = "anonymous";

    private ChatterHashUtils() {
    }

    public static Long to64BitHash(String userIdHash) {
        if (userIdHash == null || userIdHash.isBlank() || isAnonymous(userIdHash)) {
            return null;
        }

        try {
            int end = Math.min(userIdHash.length(), HASH_LENGTH);
            String hexSubstring = userIdHash.substring(0, end);
            return Long.parseUnsignedLong(hexSubstring, RADIX);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean isAnonymous(String userIdHash) {
        return ANONYMOUS.equalsIgnoreCase(userIdHash) || "null".equalsIgnoreCase(userIdHash);
    }
}
