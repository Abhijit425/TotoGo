package com.totogo.core.debug

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.io.File

/**
 * Saves the last crash to a file so the app can show it (and let the user share it) on next launch.
 * Delegates to the system handler afterwards, so normal crash behaviour is unchanged.
 */
object CrashReporter {
    private const val FILE = "last_crash.txt"

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching {
                File(app.filesDir, FILE).writeText(
                    "TotoGo ${app.packageName} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}\n" +
                        "Thread: ${thread.name}\n\n" + Log.getStackTraceString(e)
                )
            }
            previous?.uncaughtException(thread, e)
        }
    }

    fun lastCrash(context: Context): String? = File(context.filesDir, FILE).takeIf { it.exists() }?.readText()
    fun clear(context: Context) { File(context.filesDir, FILE).delete() }

    fun share(context: Context, text: String) {
        context.startActivity(
            Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share crash report")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
