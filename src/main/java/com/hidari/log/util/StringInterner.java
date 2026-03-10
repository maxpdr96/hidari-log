package com.hidari.log.util;

import java.util.concurrent.ConcurrentHashMap;

public final class StringInterner {
    private static final ConcurrentHashMap<String, String> CACHE = new ConcurrentHashMap<>(1024);

    private StringInterner() {}

    public static String intern(String s) {
        if (s == null) return null;
        return CACHE.computeIfAbsent(s, key -> key);
    }
}
