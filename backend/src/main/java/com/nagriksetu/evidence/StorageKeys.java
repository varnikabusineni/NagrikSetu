package com.nagriksetu.evidence;

import java.util.regex.Pattern;

/** Validation of server-generated storage keys: defence in depth against path traversal. */
public final class StorageKeys {

    private static final Pattern SAFE_KEY = Pattern.compile("^[a-z0-9][a-z0-9/_-]*(\\.[a-z0-9]{2,5})?$");

    private StorageKeys() {
    }

    public static String requireSafe(String key) {
        if (key == null || key.length() > 250 || !SAFE_KEY.matcher(key).matches()
                || key.contains("..") || key.contains("//") || key.endsWith("/")) {
            throw new IllegalArgumentException("Unsafe storage key");
        }
        return key;
    }
}
