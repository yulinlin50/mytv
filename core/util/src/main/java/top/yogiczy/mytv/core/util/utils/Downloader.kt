package top.yogiczy.mytv.core.util.utils

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
    
    data class DownloadState(
        val url: String,
        val filePath: String,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val lastModified: String?
    )

    suspend fun downloadTo(
        url: String,
        filePath: String,
        onProgressCb: ((Int) -> Unit)? = null,
        supportResume: Boolean = true
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val file = validateAndPrepareFile(filePath)
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
                
                val contentLength = response.body?.contentLength() ?: 0L
                val totalBytes = if (response.code == 206) {
                    contentLength + downloadedBytes
                } else {
                    contentLength
                }
                
                response.body?.byteStream()?.use { input ->
                    FileOutputStream(file, response.code == 206).use { output ->
                        val buffer = ByteArray(Buffer_Size)
                        var currentBytes = downloadedBytes
                        var bytesRead: Int
                        
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            currentBytes += bytesRead
                            
                            if (totalBytes > 0) {
                                val progress = ((currentBytes * 100) / totalBytes).toInt()
                                withContext(Dispatchers.Main) {
                                    onProgressCb?.invoke(progress)
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
    
    private fun validateAndPrepareFile(filePath: String): File {
        val file = File(filePath).canonicalFile
        
        if (!isPathSafe(file)) {
            throw SecurityException("非法的文件路径: $filePath")
        }
        
        file.parentFile?.mkdirs()
        
        return file
    }
    
    private fun isPathSafe(file: File): Boolean {
        val canonicalPath = try {
            file.canonicalPath
        } catch (e: Exception) {
            return false
        }
        
        return !canonicalPath.contains("..")
    }
    
    fun getDownloadState(filePath: String): DownloadState? {
        val file = File(filePath)
        if (!file.exists()) return null
        
        return DownloadState(
            url = "",
            filePath = filePath,
            downloadedBytes = file.length(),
            totalBytes = file.length(),
            lastModified = null
        )
    }
    
    private const val Buffer_Size = 8192
}
