package ru.yourok.torrserve.app

import android.os.Environment
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CrashLogger : Thread.UncaughtExceptionHandler {

    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(t: Thread, e: Throwable) {
        try {
            val sw = StringWriter()
            val pw = PrintWriter(sw)
            pw.println("=== TorrServe Search Crash ===")
            pw.println("Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
            pw.println("Thread: ${t.name}")
            pw.println()
            e.printStackTrace(pw)
            pw.flush()

            val dir = File(Environment.getExternalStorageDirectory(), "TorrServe")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "crash_log.txt")
            file.writeText(sw.toString())
        } catch (_: Exception) {
        }
        defaultHandler?.uncaughtException(t, e)
    }
}
