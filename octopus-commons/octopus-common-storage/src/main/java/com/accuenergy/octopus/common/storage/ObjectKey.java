package com.accuenergy.octopus.common.storage;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

public record ObjectKey(String value) {
    private static final int MAX_UTF8_BYTES = 900;
    private static final String PORTABLE_FORBIDDEN_CHARACTERS = "<>:\"|?*";
    private static final Set<String> WINDOWS_RESERVED_NAMES = Set.of(
            "CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9");

    public ObjectKey {
        if (value == null || value.isBlank() || value.startsWith("/") || value.contains("\\")
                || value.getBytes(StandardCharsets.UTF_8).length > MAX_UTF8_BYTES) {
            throw new IllegalArgumentException("Object key must be a normalized relative path");
        }
        for (String segment : value.split("/", -1)) {
            if (segment.isBlank() || segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException("Object key must be a normalized relative path");
            }
            if (segment.endsWith(".") || segment.endsWith(" ")
                    || segment.codePoints().anyMatch(ObjectKey::isForbiddenPortableCharacter)
                    || isWindowsReservedName(segment)) {
                throw new IllegalArgumentException("Object key contains a non-portable path segment");
            }
        }
        if (value.codePoints().anyMatch(codePoint -> Character.isISOControl(codePoint))) {
            throw new IllegalArgumentException("Object key cannot contain control characters");
        }
    }

    private static boolean isForbiddenPortableCharacter(int codePoint) {
        return PORTABLE_FORBIDDEN_CHARACTERS.indexOf(codePoint) >= 0;
    }

    private static boolean isWindowsReservedName(String segment) {
        String baseName = segment.split("\\.", 2)[0].toUpperCase(Locale.ROOT);
        return WINDOWS_RESERVED_NAMES.contains(baseName);
    }
}
