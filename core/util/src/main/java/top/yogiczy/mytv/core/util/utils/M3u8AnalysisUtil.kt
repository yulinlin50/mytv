package top.yogiczy.mytv.core.util.utils

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URL
import java.util.concurrent.TimeUnit

object M3u8AnalysisUtil {
    
    private const val TAG = "M3u8AnalysisUtil"
    private const val CONNECT_TIMEOUT = 10L
    private const val READ_TIMEOUT = 10L
    
    private val m3u8Client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
            .build()
    }
    
    private val semaphore = Semaphore(5)
    
    suspend fun getFirstFrame(m3u8Url: String): Bitmap? = withContext(Dispatchers.IO) {
        semaphore.withPermit {
            try {
                val tsUrl = getFirstTsUrl(m3u8Url) ?: return@withContext null
                getFrameFromTs(tsUrl)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get first frame: ${e.message}", e)
                null
            }
        }
    }
    
    private suspend fun getFirstTsUrl(m3u8Url: String): String? = withContext(Dispatchers.IO) {
        if (!m3u8Url.split("?").first().endsWith(".m3u8")) return@withContext null
        
        try {
            val request = Request.Builder().url(m3u8Url).build()
            
            m3u8Client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                
                val m3u8Content = response.body?.string() ?: return@withContext null
                
                val lines = m3u8Content.lines()
                for (line in lines) {
                    if (line.isNotEmpty() && !line.startsWith("#")) {
                        return@withContext resolveUrl(m3u8Url, line)
                    }
                }
                
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get TS URL: ${e.message}", e)
            null
        }
    }
    
    private fun resolveUrl(baseUrl: String, relativePath: String): String {
        return try {
            val base = URL(baseUrl)
            URL(base, relativePath).toString()
        } catch (e: Exception) {
            relativePath
        }
    }
    
    private suspend fun getFrameFromTs(tsUrl: String): Bitmap? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(tsUrl, mapOf())
            retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get frame from TS: ${e.message}", e)
            null
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to release retriever: ${e.message}")
            }
        }
    }
}
