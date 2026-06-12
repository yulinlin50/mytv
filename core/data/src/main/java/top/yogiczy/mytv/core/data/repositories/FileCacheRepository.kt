package top.yogiczy.mytv.core.data.repositories

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import top.yogiczy.mytv.core.data.utils.Globals
import java.io.File
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * 用于将数据缓存至本地
 */
abstract class FileCacheRepository(
    private val fileName: String,
    private val isFullPath: Boolean = false,
) {
    init {
        getCacheFile().parentFile?.let {
            if (!it.exists()) it.mkdirs()
        }
    }

    companion object {
        private val cacheFileSemaphores = ConcurrentHashMap<String, Semaphore>()

        private fun getSemaphoreForFile(filePath: String): Semaphore {
            return cacheFileSemaphores.getOrPut(filePath) { Semaphore(1) }
        }
    }

    protected fun getCacheFile() =
        if (isFullPath) File(fileName) else File(Globals.cacheDir, fileName)

    private suspend fun getCacheData(): String? = withContext(Dispatchers.IO) {
        val file = getCacheFile()
        if (file.exists()) file.readText()
        else null
    }

    protected suspend fun getCacheInputStream(): InputStream? = withContext(Dispatchers.IO) {
        val file = getCacheFile()
        if (file.exists()) file.inputStream()
        else null
    }

    private suspend fun setCacheData(data: String) = withContext(Dispatchers.IO) {
        val file = getCacheFile()
        file.writeText(data)
    }

    protected suspend fun setCacheInputStream(data: InputStream) = withContext(Dispatchers.IO) {
        val file = getCacheFile()
        file.parentFile?.mkdirs()
        data.use { input ->
            file.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }

    protected suspend fun getOrRefreshInputStream(
        cacheTime: Long,
        refreshOp: suspend () -> InputStream
    ): InputStream {
        return getOrRefreshInputStream(
            { lastModified, _ -> System.currentTimeMillis() - lastModified >= cacheTime },
            refreshOp,
        )
    }

    protected suspend fun getOrRefreshInputStream(
        isExpired: (lastModified: Long, cacheData: InputStream?) -> Boolean,
        refreshOp: suspend () -> InputStream,
    ) = withContext(Dispatchers.IO) {
        val cacheFile = getCacheFile()
        val semaphore = getSemaphoreForFile(cacheFile.absolutePath)

        semaphore.withPermit {
            var data = getCacheInputStream()

            if (isExpired(cacheFile.lastModified(), data)) {
                data?.close()
                data = null
            }

            if (data == null) {
                val newData = refreshOp()
                if (newData != null) {
                    setCacheInputStream(newData)
                    data = getCacheInputStream()
                }
            }

            data ?: throw IllegalStateException("Cache input stream is null after refresh")
        }
    }

    protected suspend fun getOrRefresh(cacheTime: Long, refreshOp: suspend () -> String): String {
        return getOrRefresh(
            { lastModified, _ -> System.currentTimeMillis() - lastModified >= cacheTime },
            refreshOp,
        )
    }

    protected suspend fun getOrRefresh(
        isExpired: (lastModified: Long, cacheData: String?) -> Boolean,
        refreshOp: suspend () -> String,
    ): String {
        val cacheFile = getCacheFile()
        val semaphore = getSemaphoreForFile(cacheFile.absolutePath)

        return semaphore.withPermit {
            var data = getCacheData()

            if (isExpired(cacheFile.lastModified(), data)) {
                data = null
            }

            if (data.isNullOrBlank()) {
                data = refreshOp()
                setCacheData(data)
            }

            data
        }
    }

    open suspend fun clearCache() = withContext(Dispatchers.IO) {
        try {
            getCacheFile().delete()
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

    fun lastModified(): Long {
        return getCacheFile().lastModified()
    }
}