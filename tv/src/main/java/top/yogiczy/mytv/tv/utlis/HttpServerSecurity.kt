package top.yogiczy.mytv.tv.utlis

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
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
 * - 加密存储 Token
 */
object HttpServerSecurity {
    private const val KEY_SERVER_TOKEN = "http_server_token"
    private const val KEY_MIGRATION_DONE = "http_server_token_migration_done"
    private const val MAX_UPLOAD_SIZE = 100 * 1024 * 1024L  // 100MB
    private const val ENCRYPTED_PREFS_NAME = "secure_http_server_prefs"
    
    private var _accessToken: String? = null
    private var encryptedPrefs: EncryptedSharedPreferences? = null
    
    private fun getEncryptedPrefs(context: Context): EncryptedSharedPreferences {
        if (encryptedPrefs == null) {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            
            encryptedPrefs = EncryptedSharedPreferences.create(
                context,
                ENCRYPTED_PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            ) as EncryptedSharedPreferences
        }
        return encryptedPrefs!!
    }
    
    private fun migrateFromPlaintext(context: Context) {
        val migrationDone = getEncryptedPrefs(context).getBoolean(KEY_MIGRATION_DONE, false)
        if (!migrationDone) {
            val plaintextToken = SP.getString(KEY_SERVER_TOKEN, "")
            if (plaintextToken.isNotBlank()) {
                getEncryptedPrefs(context).edit()
                    .putString(KEY_SERVER_TOKEN, plaintextToken)
                    .putBoolean(KEY_MIGRATION_DONE, true)
                    .apply()
                SP.remove(KEY_SERVER_TOKEN)
            } else {
                getEncryptedPrefs(context).edit()
                    .putBoolean(KEY_MIGRATION_DONE, true)
                    .apply()
            }
        }
    }
    
    fun getAccessToken(context: Context): String {
        migrateFromPlaintext(context)
        
        if (_accessToken == null) {
            _accessToken = getEncryptedPrefs(context).getString(KEY_SERVER_TOKEN, "").ifBlank {
                generateToken().also { 
                    getEncryptedPrefs(context).edit()
                        .putString(KEY_SERVER_TOKEN, it)
                        .apply()
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
    
    fun regenerateToken(context: Context): String {
        _accessToken = generateToken()
        getEncryptedPrefs(context).edit()
            .putString(KEY_SERVER_TOKEN, _accessToken!!)
            .apply()
        return _accessToken!!
    }
    
    fun validateToken(authHeader: String?, context: Context): Boolean {
        if (authHeader.isNullOrBlank()) return false
        return authHeader == "Bearer ${getAccessToken(context)}"
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
