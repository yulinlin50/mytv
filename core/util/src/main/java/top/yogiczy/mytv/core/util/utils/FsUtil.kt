package top.yogiczy.mytv.core.util.utils

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

object FsUtil {
    
    private const val TAG = "FsUtil"
    
    fun getDirSizeFlow(dir: File): Flow<Long> = flow {
        if (!isPathSafe(dir)) {
            Log.w(TAG, "Unsafe path access: ${dir.absolutePath}")
            emit(0L)
            return@flow
        }
        
        if (!dir.exists() || !dir.isDirectory) {
            emit(0L)
            return@flow
        }
        
        val totalSize = calculateDirSizeIterative(dir) { size -> emit(size) }
        emit(totalSize)
    }.flowOn(Dispatchers.IO)
    
    private suspend fun calculateDirSizeIterative(
        rootDir: File,
        onProgress: suspend (Long) -> Unit
    ): Long {
        var totalSize = 0L
        val stack = ArrayDeque<File>()
        stack.add(rootDir)
        
        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            
            try {
                val files = current.listFiles()
                if (files != null) {
                    for (file in files) {
                        if (file.isFile) {
                            val size = file.length()
                            totalSize += size
                            onProgress(size)
                        } else if (file.isDirectory && file.canRead()) {
                            stack.add(file)
                        }
                    }
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "Cannot access: ${current.absolutePath}")
            }
        }
        
        return totalSize
    }
    
    private fun isPathSafe(dir: File): Boolean {
        return try {
            val canonicalPath = dir.canonicalPath
            !canonicalPath.contains("..")
        } catch (e: Exception) {
            false
        }
    }
}
