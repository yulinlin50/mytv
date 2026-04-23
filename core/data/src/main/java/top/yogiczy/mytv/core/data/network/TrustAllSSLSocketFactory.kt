package top.yogiczy.mytv.core.data.network

import android.annotation.SuppressLint
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/**
 * 不安全的 SSL Socket Factory
 * 
 * 警告：此实现禁用了 SSL 证书验证，存在安全风险。
 * 仅应在以下情况使用：
 * 1. 开发/测试环境
 * 2. 需要访问自签名证书的直播源
 * 
 * 生产环境应使用 Network Security Config 或 Certificate Pinning
 * 
 * @see <a href="https://developer.android.com/training/articles/security-config">Network Security Config</a>
 */
object TrustAllSSLSocketFactory {
    @SuppressLint("CustomX509TrustManager")
    val trustManager = object : X509TrustManager {
        @SuppressLint("TrustAllX509TrustManager")
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
        }

        @SuppressLint("TrustAllX509TrustManager")
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> {
            return arrayOf()
        }
    }

    val sslSocketFactory by lazy {
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf(trustManager), null)
        sslContext.socketFactory!!
    }
}