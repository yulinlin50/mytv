package top.yogiczy.mytv.tv

import android.app.Application
import android.content.Intent
import android.os.Process
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.util.DebugLogger
import io.sentry.Hint
import io.sentry.Sentry
import io.sentry.SentryEvent
import io.sentry.SentryLevel
import io.sentry.SentryOptions
import io.sentry.android.core.SentryAndroid
import top.yogiczy.mytv.core.data.AppData
import top.yogiczy.mytv.core.data.utils.Globals
import top.yogiczy.mytv.tv.ui.utils.Configs

class MyTVApplication : Application(), ImageLoaderFactory {
    companion object {
        private var _imageLoader: ImageLoader? = null
    }

    override fun onCreate() {
        super.onCreate()

        initSentry()
        crashHandle()
        AppData.init(applicationContext)
        if (BuildConfig.DEBUG) {
            UnsafeTrustManager.enableUnsafeTrustManager()
        }
    }

    fun updateImageLoader() {
        val cacheEnabled = Configs.channelLogoCacheEnable
        _imageLoader = ImageLoader(this).newBuilder()
            .apply {
                if (BuildConfig.DEBUG) {
                    logger(DebugLogger())
                }
            }
            .components {
                add(SvgDecoder.Factory())
            }
            .crossfade(true)
            .memoryCachePolicy(if (cacheEnabled) CachePolicy.ENABLED else CachePolicy.DISABLED)
            .memoryCache {
                if (cacheEnabled) {
                    MemoryCache.Builder(this)
                        .maxSizePercent(0.25)
                        .build()
                } else {
                    MemoryCache.Builder(this)
                        .maxSizeBytes(0)
                        .build()
                }
            }
            .diskCachePolicy(if (cacheEnabled) CachePolicy.ENABLED else CachePolicy.DISABLED)
            .networkCachePolicy(if (cacheEnabled) CachePolicy.ENABLED else CachePolicy.DISABLED)
            .diskCache {
                if (cacheEnabled) {
                    DiskCache.Builder()
                        .directory(filesDir.resolve("channel_logo_cache"))
                        .maxSizeBytes(1024 * 1024 * 200)
                        .build()
                } else {
                    DiskCache.Builder()
                        .directory(filesDir.resolve("channel_logo_cache"))
                        .maxSizeBytes(0)
                        .build()
                }
            }
            .build()
    }

    override fun newImageLoader(): ImageLoader {
        if (_imageLoader == null) {
            updateImageLoader()
        }
        return _imageLoader!!
    }

    fun clearImageCache() {
        _imageLoader?.memoryCache?.clear()
        _imageLoader?.diskCache?.clear()
        _imageLoader = null
    }

    private fun initSentry() {
        SentryAndroid.init(this) { options ->
            options.environment = BuildConfig.BUILD_TYPE
            options.dsn = BuildConfig.SENTRY_DSN
            options.tracesSampleRate = 1.0
            options.beforeSend =
                SentryOptions.BeforeSendCallback { event: SentryEvent, _: Hint ->
                    if (event.level == null) event.level = SentryLevel.FATAL

                    if (BuildConfig.DEBUG) return@BeforeSendCallback null
                    if (SentryLevel.ERROR != event.level && SentryLevel.FATAL != event.level) return@BeforeSendCallback null
                    if (event.exceptions?.any { ex -> ex.type?.contains("Http") == true } == true) return@BeforeSendCallback null

                    event
                }
        }

        @Suppress("UnstableApiUsage")
        Sentry.withScope { scope ->
            Globals.deviceId = scope.options.distinctId ?: ""
        }
    }

    private fun crashHandle() {
        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            throwable.printStackTrace()
            Sentry.captureException(throwable)

            val intent = Intent(this, CrashHandlerActivity::class.java).apply {
                putExtra("error_message", throwable.message)
                putExtra("error_stacktrace", throwable.stackTraceToString())
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            startActivity(intent)

            Process.killProcess(Process.myPid())
        }
    }
}
