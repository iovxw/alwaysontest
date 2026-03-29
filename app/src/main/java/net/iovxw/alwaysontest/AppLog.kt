package net.iovxw.alwaysontest

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LogGroup(
    val timestamp: String,
    val lines: List<String>
)

object AppLog {
    private const val MAX_GROUPS = 50
    private val groups = mutableListOf<LogGroup>()
    private val pendingLines = mutableListOf<String>()
    private var pendingTimestamp: String? = null
    private val _groups = MutableStateFlow<List<LogGroup>>(emptyList())
    val logGroups = _groups.asStateFlow()
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    fun d(tag: String, msg: String) {
        Log.d(tag, msg)
        append("D/$tag: $msg")
    }

    fun w(tag: String, msg: String) {
        Log.w(tag, msg)
        append("W/$tag: $msg")
    }

    fun e(tag: String, msg: String, t: Throwable? = null) {
        if (t != null) Log.e(tag, msg, t) else Log.e(tag, msg)
        append("E/$tag: $msg${if (t != null) " (${t.message})" else ""}")
    }

    /** Start a new card group. Subsequent log lines go into this group until next newGroup(). */
    fun newGroup() {
        synchronized(groups) {
            flushPending()
        }
    }

    private fun append(line: String) {
        synchronized(groups) {
            if (pendingTimestamp == null) {
                pendingTimestamp = timeFormat.format(Date())
            }
            pendingLines.add(line)
            publish()
        }
    }

    private fun flushPending() {
        if (pendingLines.isNotEmpty()) {
            val ts = pendingTimestamp ?: timeFormat.format(Date())
            groups.add(LogGroup(ts, pendingLines.toList()))
            pendingLines.clear()
            pendingTimestamp = null
            if (groups.size > MAX_GROUPS) {
                groups.removeAt(0)
            }
        }
    }

    private fun publish() {
        val snapshot = mutableListOf<LogGroup>()
        snapshot.addAll(groups)
        if (pendingLines.isNotEmpty()) {
            val ts = pendingTimestamp ?: timeFormat.format(Date())
            snapshot.add(LogGroup(ts, pendingLines.toList()))
        }
        _groups.value = snapshot
    }

    fun clear() {
        synchronized(groups) {
            groups.clear()
            pendingLines.clear()
            pendingTimestamp = null
            _groups.value = emptyList()
        }
    }
}
