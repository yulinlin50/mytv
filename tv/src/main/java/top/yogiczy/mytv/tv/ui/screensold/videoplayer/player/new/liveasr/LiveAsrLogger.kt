package top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.new.liveasr

import android.content.Context
import android.util.Log
import top.yogiczy.mytv.tv.ui.utils.Configs
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 实时字幕翻译功能专用日志工具
 * 日志同时输出到 Logcat 和文件（app私有目录/liveasr_log/）
 * 文件按日期滚动，保留最近3天的日志
 * 文件日志受 SUBTITLE_LIVE_DEBUG_LOG 开关控制
 */
object LiveAsrLogger {

    private const val TAG = "LiveAsr"
    private const val LOG_DIR = "liveasr_log"
    private const val MAX_LOG_DAYS = 3

    @Volatile
    private var logDir: File? = null

    /** 初始化日志目录，带 Context 时设置文件输出目录 */
    fun init(context: Context? = null) {
        if (context != null && logDir == null) {
            logDir = File(context.filesDir, LOG_DIR).also { it.mkdirs() }
            cleanOldLogs()
        }
    }

    fun d(msg: String) {
        Log.d(TAG, msg)
        writeToFile("D", msg)
    }

    fun i(msg: String) {
        Log.i(TAG, msg)
        writeToFile("I", msg)
    }

    fun w(msg: String, e: Throwable? = null) {
        if (e != null) Log.w(TAG, msg, e) else Log.w(TAG, msg)
        writeToFile("W", msg, e)
    }

    fun e(msg: String, e: Throwable? = null) {
        if (e != null) Log.e(TAG, msg, e) else Log.e(TAG, msg)
        writeToFile("E", msg, e)
    }

    private fun writeToFile(level: String, msg: String, e: Throwable? = null) {
        // 检查日志开关，未开启则不写文件
        if (!Configs.subtitleLiveDebugLog) return

        val dir = logDir ?: return
        try {
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val file = File(dir, "liveasr_$date.log")
            val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
            val line = "$timestamp $level $msg"

            PrintWriter(FileWriter(file, true)).use { pw ->
                pw.println(line)
                e?.let { pw.println("$timestamp E ${it.stackTraceToString()}") }
            }
        } catch (_: Exception) {
            // 日志写入失败不影响主流程
        }
    }

    /** 清理超过 MAX_LOG_DAYS 天的旧日志 */
    private fun cleanOldLogs() {
        val dir = logDir ?: return
        try {
            val cutoff = System.currentTimeMillis() - MAX_LOG_DAYS * 24 * 60 * 60 * 1000L
            dir.listFiles()?.forEach { file ->
                if (file.lastModified() < cutoff) file.delete()
            }
        } catch (_: Exception) {}
    }
}
