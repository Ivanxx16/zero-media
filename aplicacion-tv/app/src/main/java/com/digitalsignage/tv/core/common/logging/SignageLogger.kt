package com.digitalsignage.tv.core.common.logging

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue

enum class LogLevel {
    DEBUG, INFO, WARN, ERROR
}

data class LogEntry(
    val timestamp: Long,
    val level: LogLevel,
    val tag: String,
    val message: String,
    val throwable: Throwable? = null
) {
    private val formatter = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    fun formatted(): String = "[${formatter.format(Date(timestamp))}] [$level] [$tag] $message"
}

object SignageLogger {

    private const val MAX_BUFFER_SIZE = 200
    private val buffer = ConcurrentLinkedQueue<LogEntry>()
    var isDebugEnabled: Boolean = true

    fun d(tag: String, message: String) = log(LogLevel.DEBUG, tag, message, null)
    fun i(tag: String, message: String) = log(LogLevel.INFO, tag, message, null)
    fun w(tag: String, message: String, throwable: Throwable? = null) = log(LogLevel.WARN, tag, message, throwable)
    fun e(tag: String, message: String, throwable: Throwable? = null) = log(LogLevel.ERROR, tag, message, throwable)

    private fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = message,
            throwable = throwable
        )

        buffer.add(entry)
        while (buffer.size > MAX_BUFFER_SIZE) {
            buffer.poll()
        }

        if (isDebugEnabled) {
            when (level) {
                LogLevel.DEBUG -> Log.d(tag, message, throwable)
                LogLevel.INFO -> Log.i(tag, message, throwable)
                LogLevel.WARN -> Log.w(tag, message, throwable)
                LogLevel.ERROR -> Log.e(tag, message, throwable)
            }
        }
    }

    fun getRecentLogs(): List<LogEntry> = buffer.toList()

    fun clear() {
        buffer.clear()
    }
}
