package top.yogiczy.mytv.core.util.utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

object Downloader {
    
    private const val TAG = "Downloader"
    private const val BUFFER_SIZE = 8192
    
    private val downloadClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    suspend fun downloadTo(
        url: String,
        filePath: String,
        onProgressCb: ((Int) -> Unit)? = null,
        supportResume: Boolean = true,
        context: Context
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val file = validateAndPrepareFile(filePath, context)
            val downloadedBytes = if (supportResume && file.exists()) file.length() else 0L
            
            val request = Request.Builder()
                .url(url)
                .apply {
                    if (downloadedBytes > 0) {
                        header("Range", "bytes=$downloadedBytes-")
                    }
                }
                .build()
            
            downloadClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful && response.code != 206) {
                    return@withContext Result.failure(
                        Exception("下载失败: HTTP ${response.code}")
                    )
                }
                
                val isResume = response.code == 206
                val contentLength = response.body?.contentLength() ?: 0L
                val totalBytes = if (isResume) {
                    contentLength + downloadedBytes
                } else {
                    contentLength
                }
                
                response.body?.byteStream()?.use { input ->
                    FileOutputStream(file, isResume).use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var currentBytes = if (isResume) downloadedBytes else 0L
                        var lastReportedProgress = -1
                        var bytesRead: Int
                        
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            currentBytes += bytesRead
                            
                            if (totalBytes > 0) {
                                val progress = ((currentBytes * 100) / totalBytes).toInt()
                                if (progress != lastReportedProgress) {
                                    lastReportedProgress = progress
                                    withContext(Dispatchers.Main) {
                                        onProgressCb?.invoke(progress)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Download failed: ${e.message}", e)
            Result.failure(Exception("下载失败: ${e.message}", e))
        }
    }
    
    private fun validateAndPrepareFile(filePath: String, context: Context): File {
        val file = File(filePath).canonicalFile
        
        if (!isPathSafe(file, context)) {
            throw SecurityException("非法的文件路径: $filePath")
        }
        
        file.parentFile?.mkdirs()
        
        return file
    }
    
    private fun isPathSafe(file: File, context: Context): Boolean {
        val canonicalPath = try {
            file.canonicalPath
        } catch (e: Exception) {
            return false
        }
        
        val allowedDirs = listOfNotNull(
            context.cacheDir,
            context.filesDir,
            context.externalCacheDir,
            context.getExternalFilesDir(null)
        )
        return allowedDirs.any { dir ->
            canonicalPath.startsWith(dir.canonicalPath)
        }
    }
}
