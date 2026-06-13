package top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.new.liveasr

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * 模型下载管理器
 *
 * 统一管理 Vosk / Whisper 模型的运行时下载：
 * 1. 选择引擎 → 检查模型是否已下载
 * 2. 未下载 → 通知栏显示进度下载
 * 3. 下载完自动初始化
 *
 * 模型存储路径：context.filesDir/models/
 */
object ModelManager {

    // ==================== 预定义模型 ====================

    /** Vosk 英语识别模型 */
    val VOSK_EN = ModelInfo(
        id = "vosk-en",
        name = "Vosk 英语模型",
        downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip",
        destDir = "vosk-model-small-en-us-0.15",
        sizeMB = 40,
        isZip = true,
    )

    /** Vosk 中文识别模型（后续扩展） */
    val VOSK_ZH = ModelInfo(
        id = "vosk-zh",
        name = "Vosk 中文模型",
        downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-cn-0.22.zip",
        destDir = "vosk-model-small-cn-0.22",
        sizeMB = 42,
        isZip = true,
    )

    /** Whisper ggml-tiny 模型 */
    val WHISPER_TINY = ModelInfo(
        id = "whisper-tiny",
        name = "Whisper Tiny 模型",
        downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin",
        destDir = "whisper-tiny",
        destFileName = "ggml-tiny.bin",
        sizeMB = 75,
    )

    /** Whisper ggml-base 模型 */
    val WHISPER_BASE = ModelInfo(
        id = "whisper-base",
        name = "Whisper Base 模型",
        downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin",
        destDir = "whisper-base",
        destFileName = "ggml-base.bin",
        sizeMB = 142,
    )

    // ==================== 数据类 ====================

    data class ModelInfo(
        val id: String,              // 唯一标识
        val name: String,            // 显示名称
        val downloadUrl: String,     // 下载地址
        val destDir: String,         // 解压/存放目录名
        val destFileName: String = "", // 单文件模型的文件名（非 ZIP 时使用）
        val sizeMB: Int,             // 预估大小（用于 UI 展示）
        val isZip: Boolean = false,  // 是否需解压
    )

    // ==================== 内部常量 ====================

    private const val CHANNEL_ID = "model_download"
    private const val MODELS_ROOT = "models"

    // ==================== 公开方法 ====================

    /** 获取模型存储根目录 */
    fun getModelsDir(context: Context): File =
        File(context.filesDir, MODELS_ROOT).also { it.mkdirs() }

    /** 获取指定模型的存储目录 */
    fun getModelDir(context: Context, info: ModelInfo): File =
        File(getModelsDir(context), info.destDir)

    /** 判断模型是否已下载 */
    fun isDownloaded(context: Context, info: ModelInfo): Boolean {
        val dir = getModelDir(context, info)
        if (!dir.exists()) return false
        val files = dir.listFiles() ?: return false
        return files.isNotEmpty() && files.any { it.length() > 100 * 1024 }
    }

    /**
     * 确保模型可用：已下载则直接返回路径，否则下载
     * 下载过程通过通知栏显示进度
     *
     * @return 模型所在目录路径
     */
    suspend fun ensureModel(context: Context, info: ModelInfo): File = withContext(Dispatchers.IO) {
        val dest = getModelDir(context, info)
        if (isDownloaded(context, info)) return@withContext dest

        dest.mkdirs()

        val tempFile = File(getModelsDir(context), "${info.id}.tmp")
        val url = URL(info.downloadUrl)

        createNotificationChannel(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 初始通知
        showProgressNotification(context, nm, info, 0)

        try {
            // 下载
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 30000
                readTimeout = 120000
                setRequestProperty("User-Agent", "Mozilla/5.0")
                connect()
            }

            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                conn.disconnect()
                throw RuntimeException("HTTP ${conn.responseCode}")
            }

            val total = conn.contentLength.toLong()
            var downloaded = 0L
            var lastNotifyTime = 0L

            conn.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buf = ByteArray(8192)
                    var n: Int
                    while (input.read(buf).also { n = it } != -1) {
                        output.write(buf, 0, n)
                        downloaded += n

                        val now = System.currentTimeMillis()
                        if (now - lastNotifyTime >= 500) {
                            lastNotifyTime = now
                            val pct = if (total > 0) (downloaded * 100 / total).toInt() else 0
                            showProgressNotification(context, nm, info, pct)
                        }
                    }
                }
            }
            conn.disconnect()

            // 解压或移动
            if (info.isZip) {
                unzip(tempFile, dest)
            } else {
                val target = File(dest, info.destFileName.ifBlank { info.destDir })
                tempFile.renameTo(target)
            }
            tempFile.delete()

            // 完成通知
            showDoneNotification(context, nm, info)
        } catch (e: Exception) {
            tempFile.delete()
            dest.deleteRecursively()
            showErrorNotification(context, nm, info, e.message)
            throw RuntimeException("${info.name} 下载失败: ${e.message}", e)
        }

        dest
    }

    // ==================== 私有方法 ====================

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "模型下载", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "语音识别模型下载进度" }
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    private fun showProgressNotification(
        ctx: Context, nm: NotificationManager, info: ModelInfo, pct: Int,
    ) {
        nm.notify(info.id.hashCode(), NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setContentTitle("正在下载 ${info.name}")
            .setContentText("${pct}%")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, pct, false)
            .setOngoing(true)
            .build()
        )
    }

    private fun showDoneNotification(ctx: Context, nm: NotificationManager, info: ModelInfo) {
        nm.notify(info.id.hashCode(), NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setContentTitle("${info.name} 下载完成")
            .setContentText("模型已就绪，可以使用了")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setProgress(0, 0, false)
            .setAutoCancel(true)
            .build()
        )
    }

    private fun showErrorNotification(ctx: Context, nm: NotificationManager, info: ModelInfo, msg: String?) {
        nm.notify(info.id.hashCode(), NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setContentTitle("${info.name} 下载失败")
            .setContentText(msg ?: "未知错误")
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setProgress(0, 0, false)
            .setAutoCancel(true)
            .build()
        )
    }

    /** 解压 ZIP 文件，自动剥离顶层目录 */
    private fun unzip(zipFile: File, destDir: File) {
        ZipInputStream(zipFile.inputStream().buffered()).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            // 记录顶层目录名，用于后续剥离
            var topDir: String? = null

            while (entry != null) {
                val name = entry.name

                if (entry.isDirectory) {
                    if (topDir == null && name.count { it == '/' } == 1) {
                        topDir = name.substringBefore("/") + "/"
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                    continue
                }

                // 剥离顶层目录前缀
                val stripped = if (topDir != null && name.startsWith(topDir)) {
                    name.removePrefix(topDir)
                } else {
                    name.substringAfter("/", name)
                }

                val outFile = File(destDir, stripped)
                outFile.parentFile?.mkdirs()

                FileOutputStream(outFile).use { fos -> zis.copyTo(fos) }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }
}