package top.yogiczy.mytv.core.data.utils

import android.util.Log
import java.net.URI

object InputValidator {
    
    private const val TAG = "InputValidator"
    
    private val VALID_SCHEMES = setOf("http", "https", "rtmp", "rtsp", "rtmps", "mms")
    
    fun isValidUrl(url: String): Boolean {
        if (url.isBlank()) return false
        
        return try {
            val uri = URI(url)
            uri.scheme?.lowercase() in VALID_SCHEMES && !uri.host.isNullOrBlank()
        } catch (e: Exception) {
            Log.w(TAG, "无效的 URL 格式: $url")
            false
        }
    }
    
    fun sanitizeUrl(url: String): String {
        return url.trim().let { 
            if (!it.contains("://") && !it.startsWith("file:")) {
                "http://$it"
            } else {
                it
            }
        }
    }
    
    fun isValidChannelName(name: String): Boolean {
        return name.isNotBlank() && name.length <= 100
    }
    
    fun sanitizeChannelName(name: String): String {
        return name.trim().take(100)
    }
}
