package xyz.wallpanel.app.utils

import android.util.Log
import timber.log.Timber

/**
 * Lightweight release logging without Firebase background services.
 * Warnings and errors remain available through adb logcat.
 */
class WallpanelReleaseTree : Timber.Tree() {
    override fun log(priority: Int, tag: String?, message: String, throwable: Throwable?) {
        if (priority < Log.WARN) return
        val details = if (throwable == null) message
            else message + "\n" + Log.getStackTraceString(throwable)
        Log.println(priority, tag ?: "WallPanel", details)
    }
}
