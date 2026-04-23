package top.yogiczy.mytv.tv.utlis

import top.yogiczy.mytv.core.data.utils.Globals
import top.yogiczy.mytv.core.data.utils.SP
import top.yogiczy.mytv.tv.ui.utils.Configs
import java.io.File
import java.security.SecureRandom
import java.util.Base64

/**
 * HTTP 服务器安全工具类
 * 
 * 提供：
 * - Token 验证
 * - 路径验证
 * - 文件大小限制
 */
object HttpServerSecurity {
    private const val KEY_SERVER_TOKEN = "http_server_token"
    private const val MAX_UPLOAD_SIZE = 100 * 1024 * 1024L  // 100MB
    
    private var _accessToken: String? = null
    
    val accessToken: String
        get() {
            if (_accessToken == null) {
                _accessToken = SP.getString(KEY_SERVER_TOKEN, "").ifBlank {
                    generateToken().also { 
                        SP.putString(KEY_SERVER_TOKEN, it)
                    }
                }
            }
            return _accessToken!!
        }
    
    private fun generateToken(): String {
        val random = SecureRandom()
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
    
    fun regenerateToken(): String {
        _accessToken = generateToken()
        SP.putString(KEY_SERVER_TOKEN, _accessToken!!)
        return _accessToken!!
    }
    
    fun validateToken(authHeader: String?): Boolean {
        if (authHeader.isNullOrBlank()) return false
        return authHeader == "Bearer $accessToken"
    }
    
    fun isPathAllowed(path: String): Boolean {
        val allowedDirs = listOf(
            Globals.fileDir.canonicalPath,
            Globals.cacheDir.canonicalPath
        )
        
        val canonicalPath = try {
            File(path).canonicalPath
        } catch (e: Exception) {
            return false
        }
        
        return allowedDirs.any { canonicalPath.startsWith(it) }
    }
    
    fun isFileSizeAllowed(contentLength: Long?): Boolean {
        if (contentLength == null) return true
        return contentLength <= MAX_UPLOAD_SIZE
    }
    
    fun sanitizeFilename(filename: String): String {
        return filename
            .replace("..", "")
            .replace("/", "_")
            .replace("\\", "_")
            .replace(":", "_")
            .take(255)
    }
}
