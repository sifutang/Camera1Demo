package com.example.cameraonedemo.utils;

import java.util.concurrent.ConcurrentHashMap;

public final class PerformanceUtil {

    private static final String TAG = "PerformanceUtil";

    private ConcurrentHashMap<String, Long> mConcurrentHashMap;

    private PerformanceUtil() {
        mConcurrentHashMap = new ConcurrentHashMap<>();
    }

    private static class H {
        private static final PerformanceUtil INSTANCE = new PerformanceUtil();
    }

    public static PerformanceUtil getInstance() {
        return H.INSTANCE;
    }

    public void logTraceStart(String key) {
        mConcurrentHashMap.put(
                key,
                System.currentTimeMillis()
        );
        Logger.logD(TAG, key + " start...");
    }

    public long logTraceEnd(String key) {
        Long start = mConcurrentHashMap.get(key);
        if (start == null) {
            return -1;
        }

        mConcurrentHashMap.remove(key);
        long consume = System.currentTimeMillis() - start;
        Logger.logD(TAG, key + " end...consume = " + consume + "ms");
        return consume;
    }
}
