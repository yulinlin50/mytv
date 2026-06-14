#include <jni.h>
#include <string.h>
#include <stdlib.h>
#include "whisper.h"

/**
 * Whisper.cpp JNI 桥接层
 *
 * 将 Kotlin 声明的 native 方法映射到 whisper.cpp C API 调用。
 * 上下文指针以 jlong（Long）在 Java/Kotlin 侧传递。
 */

/* ── initContext ──────────────────────────────────────────── */
JNIEXPORT jlong JNICALL
Java_top_yogiczy_mytv_tv_ui_screensold_videoplayer_player_new_liveasr_WhisperJni_initContext(
        JNIEnv *env, jclass clazz, jstring modelPath) {
    (void) clazz;

    if (!modelPath) {
        return 0;
    }

    const char *path = (*env)->GetStringUTFChars(env, modelPath, NULL);
    if (!path) {
        return 0;
    }

    struct whisper_context *ctx = whisper_init_from_file_with_params(path, whisper_context_default_params());
    (*env)->ReleaseStringUTFChars(env, modelPath, path);

    return (jlong) ctx;
}

/* ── freeContext ──────────────────────────────────────────── */
JNIEXPORT void JNICALL
Java_top_yogiczy_mytv_tv_ui_screensold_videoplayer_player_new_liveasr_WhisperJni_freeContext(
        JNIEnv *env, jclass clazz, jlong context) {
    (void) env;
    (void) clazz;

    if (context) {
        whisper_free((struct whisper_context *) context);
    }
}

/* ── fullTranscribe ───────────────────────────────────────── */
JNIEXPORT jstring JNICALL
Java_top_yogiczy_mytv_tv_ui_screensold_videoplayer_player_new_liveasr_WhisperJni_fullTranscribe(
        JNIEnv *env, jclass clazz, jlong context, jfloatArray pcmData) {
    (void) clazz;

    if (!context) {
        return NULL;
    }

    jsize len = (*env)->GetArrayLength(env, pcmData);
    if (len <= 0) {
        return NULL;
    }

    jfloat *data = (*env)->GetFloatArrayElements(env, pcmData, NULL);
    if (!data) {
        return NULL;
    }

    /* 配置 whisper 全量推理参数 - 针对实时场景优化 */
    struct whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_progress   = false;
    params.print_timestamps = false;
    params.print_special    = false;
    params.single_segment   = true;    /* 只输出一个 segment，减少解码时间 */
    params.no_timestamps    = true;     /* 不计算时间戳，加速推理 */
    params.language         = "auto";
    params.n_threads        = 4;        /* 限制线程数，避免与音频线程争抢 */
    params.max_len          = 0;        /* 不限制输出长度 */
    params.suppress_blank   = true;     /* 抑制空白输出 */

    int ret = whisper_full((struct whisper_context *) context, params, data, len);
    (*env)->ReleaseFloatArrayElements(env, pcmData, data, JNI_ABORT);

    if (ret != 0) {
        return NULL;
    }

    /* 拼接所有识别片段 */
    const int n_segments = whisper_full_n_segments((struct whisper_context *) context);
    if (n_segments <= 0) {
        return NULL;
    }

    /* 大多数场景只有 1 个 segment，直接取即可 */
    const char *text = whisper_full_get_segment_text((struct whisper_context *) context, 0);
    if (!text || strlen(text) == 0) {
        return NULL;
    }

    return (*env)->NewStringUTF(env, text);
}
