package com.github.catvod.utils;

import android.util.Log;

/**
 * 简易日志工具（供 spider 调试日志使用）。
 *
 * 项目内 WanMei / YingHua / Mogu / YueGuang 等蜘蛛用 Logger.log("DEBUG", "...") 打日志，
 * 此处用 Android Logcat 实现，tag 与 msg 两个参数。
 */
public class Logger {

    private static final String TAG = "CatVodSpider";

    private Logger() {
    }

    public static void log(String tag, String msg) {
        if (msg == null) return;
        if (tag == null) tag = TAG;
        try {
            Log.i(tag, msg);
        } catch (Throwable ignored) {
        }
    }

    public static void log(String msg) {
        log(TAG, msg);
    }

    public static void v(String tag, String msg) {
        try { Log.v(tag, msg); } catch (Throwable ignored) { }
    }

    public static void d(String tag, String msg) {
        try { Log.d(tag, msg); } catch (Throwable ignored) { }
    }

    public static void i(String tag, String msg) {
        try { Log.i(tag, msg); } catch (Throwable ignored) { }
    }

    public static void w(String tag, String msg) {
        try { Log.w(tag, msg); } catch (Throwable ignored) { }
    }

    public static void e(String tag, String msg) {
        try { Log.e(tag, msg); } catch (Throwable ignored) { }
    }

    public static void e(String tag, String msg, Throwable tr) {
        try { Log.e(tag, msg, tr); } catch (Throwable ignored) { }
    }
}