package com.example.cameraonedemo.utils;

import android.util.Log;

public class Logger {

    public static final boolean DEBUG = true;

    public static void logD(String tag, String msg) {
        if (DEBUG) {
            Log.d(tag, msg);
        }
    }

    public static void logI(String tag, String msg) {
        if (DEBUG) {
            Log.i(tag, msg);
        }
    }

    public static void logW(String tag, String msg) {
        if (DEBUG) {
            Log.w(tag, msg);
        }
    }

    public static void logE(String tag, String msg) {
        if (DEBUG) {
            Log.e(tag, msg);
        }
    }

    public static void logV(String tag, String msg) {
        if (DEBUG) {
            Log.v(tag, msg);
        }
    }
}
