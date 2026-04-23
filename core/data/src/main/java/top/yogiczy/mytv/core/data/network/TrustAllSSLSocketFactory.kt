package top.yogiczy.mytv.core.data.network

import android.annotation.SuppressLint
import android.util.Log
import java.net.HttpURLConnection
import java.security.KeyStore
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

object TrustAllSSLSocketFactory {
    
    private const val TAG = "TrustAllSSLSocketFactory"
    
    private val unsafeTrustManager = object : X509TrustManager {
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
    
    private val safeTrustManager: X509TrustManager by lazy {
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(null as KeyStore?)
        factory.trustManagers.filterIsInstance<X509TrustManager>().firstOrNull()
            ?: throw IllegalStateException("No X509TrustManager found in default TrustManagerFactory")
    }
    
    private val unsafeSslSocketFactory: SSLSocketFactory by lazy {
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf(unsafeTrustManager), null)
        sslContext.socketFactory!!
    }
    
    fun getSSLSocketFactory(trustAll: Boolean): SSLSocketFactory {
        return if (trustAll) {
            Log.w(TAG, "Using unsafe SSL configuration - certificate validation disabled")
            unsafeSslSocketFactory
        } else {
            SSLContext.getDefault().socketFactory
        }
    }
    
    fun getTrustManager(trustAll: Boolean): X509TrustManager {
        return if (trustAll) {
            unsafeTrustManager
        } else {
            safeTrustManager
        }
    }
    
    fun getHostnameVerifier(trustAll: Boolean): HostnameVerifier {
        return if (trustAll) {
            HostnameVerifier { _, _ -> true }
        } else {
            HttpsURLConnection.getDefaultHostnameVerifier()
        }
    }
}
