package top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.new.liveasr

/**
 * Whisper.cpp JNI 封装层
 *
 * 直接映射 whisper.cpp 核心 C API，通过 JNI 调用原生库。
 * 原生库由 CMake 编译 whisper.cpp 源码生成。
 *
 * 注意：原生库采用延迟加载，仅在首次调用时加载，
 * 避免未使用 Whisper 引擎时触发 UnsatisfiedLinkError。
 */
object WhisperJni {

    @Volatile
    private var loaded = false

    private fun ensureLoaded() {
        if (!loaded) {
            synchronized(this) {
                if (!loaded) {
                    System.loadLibrary("whisper-jni")
                    loaded = true
                }
            }
        }
    }

    // ==================== JNI 原生方法 ====================

    /**
     * 从模型文件初始化 Whisper 上下文
     * @param modelPath ggml 模型文件绝对路径
     * @return 上下文指针（Long），0 表示失败
     */
    @JvmStatic
    external fun initContext(modelPath: String): Long

    /**
     * 释放 Whisper 上下文
     * @param context 上下文指针
     */
    @JvmStatic
    external fun freeContext(context: Long)

    /**
     * 对 PCM 音频数据进行全量推理
     * @param context 上下文指针
     * @param pcmData 16kHz 单声道 float 音频数据（归一化到 [-1, 1]）
     * @return 识别文本，失败返回 null
     */
    @JvmStatic
    external fun fullTranscribe(context: Long, pcmData: FloatArray): String?

    // ==================== 辅助方法 ====================

    /**
     * 加载原生库并初始化 Whisper 上下文
     * @return 上下文指针，0 表示失败
     */
    fun init(modelPath: String): Long {
        ensureLoaded()
        return initContext(modelPath)
    }

    /**
     * 释放上下文（安全调用，已加载时才执行）
     */
    fun free(context: Long) {
        if (context != 0L && loaded) {
            freeContext(context)
        }
    }

    /**
     * 执行推理（安全调用，已加载时才执行）
     */
    fun transcribe(context: Long, pcmData: FloatArray): String? {
        if (!loaded || context == 0L) return null
        return fullTranscribe(context, pcmData)
    }

    /**
     * 将 16bit PCM ByteArray 转换为 FloatArray（归一化到 [-1, 1]）
     */
    fun pcmBytesToFloats(pcmBytes: ByteArray): FloatArray {
        val floatCount = pcmBytes.size / 2
        val floats = FloatArray(floatCount)
        for (i in 0 until floatCount) {
            val low = pcmBytes[i * 2].toInt() and 0xFF
            val high = pcmBytes[i * 2 + 1].toInt()
            val sample = (high shl 8) or low
            floats[i] = sample / 32768f
        }
        return floats
    }
}
