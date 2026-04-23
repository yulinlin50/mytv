package top.yogiczy.mytv.tv

import android.annotation.SuppressLint
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * 不安全的 TrustManager
 * 
 * 警告：此实现禁用了 SSL 证书验证，存在严重安全风险：
 * - 容易受到中间人攻击（MITM）
 * - 用户数据可能被窃取
 * - 直播内容可能被篡改
 * 
 * 仅应在以下情况使用：
 * 1. 开发/测试环境
 * 2. 需要访问自签名证书的直播源（且用户明确了解风险）
 * 
 * 生产环境应使用 Network Security Config 或 Certificate Pinning
 * 
 * @see <a href="https://developer.android.com/training/articles/security-config">Network Security Config</a>
 */
@SuppressLint("CustomX509TrustManager")
class UnsafeTrustManager : X509TrustManager {
    @SuppressLint("TrustAllX509TrustManager")
    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
    }

    @SuppressLint("TrustAllX509TrustManager")
    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> {
        return emptyArray()
    }

    companion object {
        private var isEnabled = false
        
        fun isEnabled(): Boolean = isEnabled
        
        fun enableUnsafeTrustManager() {
            if (isEnabled) return
            
            try {
                val trustAllCerts = arrayOf<TrustManager>(UnsafeTrustManager())
                val sslContext = SSLContext.getInstance("TLS")
                sslContext.init(null, trustAllCerts, SecureRandom())
                HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.socketFactory)
                HttpsURLConnection.setDefaultHostnameVerifier { _, _ -> true }
                isEnabled = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        fun disableUnsafeTrustManager() {
            if (!isEnabled) return
            
            try {
                HttpsURLConnection.setDefaultSSLSocketFactory(null)
                HttpsURLConnection.setDefaultHostnameVerifier(null)
                isEnabled = false
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
