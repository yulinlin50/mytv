cmake_minimum_required(VERSION 3.18.1)
project("whisper-jni")

include(FetchContent)

# ── 通过 FetchContent 自动下载 whisper.cpp 源码 ──────────────
# 构建时自动下载，无需 git submodule，CI 零配置
# 如需更新版本，修改 GIT_TAG 即可
FetchContent_Declare(
    whisper_cpp
    GIT_REPOSITORY https://github.com/ggerganov/whisper.cpp.git
    GIT_TAG        v1.7.5
)

# ── 编译 whisper.cpp 核心静态库 ──────────────────────────────
# 只编译 whisper 库本身，不编译 examples / tests / ggml-metal 等
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(GGML_METAL             OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP            OFF CACHE BOOL "" FORCE)

FetchContent_MakeAvailable(whisper_cpp)

# ── JNI 桥接库 ──────────────────────────────────────────────
add_library(whisper-jni SHARED whisper-jni.c)

target_include_directories(whisper-jni PRIVATE
    ${whisper_cpp_SOURCE_DIR}/include
    ${whisper_cpp_SOURCE_DIR}/ggml/include
)

target_link_libraries(whisper-jni
    whisper
    log
)
