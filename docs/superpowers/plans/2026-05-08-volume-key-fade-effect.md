# 音量键触发淡入淡出效果实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让手机音量键同时调节系统音量和播放器音量，并触发淡入淡出效果。

**Architecture:** 创建全局播放器音量管理器单例，在 MainActivity 中拦截音量键事件，同时调节系统音量和播放器音量。

**Tech Stack:** Kotlin, Android AudioManager, Compose

---

## 文件结构

| 文件 | 操作 | 说明 |
|------|------|------|
| `tv/.../videoplayer/player/new/VideoPlayerVolumeManager.kt` | 创建 | 全局播放器音量管理器 |
| `tv/.../videoplayer/player/new/VideoPlayerStateNew.kt` | 修改 | 注册/注销到管理器 |
| `tv/.../MainActivity.kt` | 修改 | 拦截音量键事件 |

---

### Task 1: 创建 VideoPlayerVolumeManager

**Files:**
- Create: `tv/src/main/java/top/yogiczy/mytv/tv/ui/screensold/videoplayer/player/new/VideoPlayerVolumeManager.kt`

- [ ] **Step 1: 创建 VideoPlayerVolumeManager 单例类**

```kotlin
package top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.new

import java.lang.ref.WeakReference

object VideoPlayerVolumeManager {
    private var currentPlayerRef: WeakReference<IVideoPlayer>? = null
    
    fun register(player: IVideoPlayer) {
        currentPlayerRef = WeakReference(player)
    }
    
    fun unregister(player: IVideoPlayer) {
        if (currentPlayerRef?.get() == player) {
            currentPlayerRef = null
        }
    }
    
    fun setVolume(volume: Float) {
        currentPlayerRef?.get()?.setVolume(volume)
    }
    
    fun hasActivePlayer(): Boolean {
        return currentPlayerRef?.get() != null
    }
}
```

- [ ] **Step 2: 提交代码**

```bash
git add tv/src/main/java/top/yogiczy/mytv/tv/ui/screensold/videoplayer/player/new/VideoPlayerVolumeManager.kt
git commit -m "feat: add VideoPlayerVolumeManager for global volume control"
```

---

### Task 2: 修改 VideoPlayerStateNew 注册到管理器

**Files:**
- Modify: `tv/src/main/java/top/yogiczy/mytv/tv/ui/screensold/videoplayer/player/new/VideoPlayerStateNew.kt`

- [ ] **Step 1: 在 initialize() 方法中注册播放器**

在 `VideoPlayerStateNew` 类中，找到 `initialize()` 方法，添加注册逻辑：

```kotlin
fun initialize() {
    instance.initialize()
    VideoPlayerVolumeManager.register(instance)
}
```

- [ ] **Step 2: 在 release() 方法中注销播放器**

在 `VideoPlayerStateNew` 类中，找到 `release()` 方法，在开头添加注销逻辑：

```kotlin
fun release() {
    if (isReleased) return
    isReleased = true
    
    VideoPlayerVolumeManager.unregister(instance)
    
    instance.release()
    
    onReadyListeners.clear()
    onErrorListeners.clear()
    onInterruptListeners.clear()
    onIsBufferingListeners.clear()
}
```

- [ ] **Step 3: 提交代码**

```bash
git add tv/src/main/java/top/yogiczy/mytv/tv/ui/screensold/videoplayer/player/new/VideoPlayerStateNew.kt
git commit -m "feat: register player to VideoPlayerVolumeManager"
```

---

### Task 3: 修改 MainActivity 拦截音量键

**Files:**
- Modify: `tv/src/main/java/top/yogiczy/mytv/tv/MainActivity.kt`

- [ ] **Step 1: 添加必要的 import**

在文件顶部添加：

```kotlin
import android.media.AudioManager
import android.view.KeyEvent
import top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.new.VideoPlayerVolumeManager
```

- [ ] **Step 2: 添加 AudioManager 属性**

在 `MainActivity` 类中添加 `audioManager` 属性：

```kotlin
class MainActivity : ComponentActivity() {
    private val audioManager: AudioManager by lazy {
        getSystemService(AUDIO_SERVICE) as AudioManager
    }
    
    // ... 其余代码
}
```

- [ ] **Step 3: 重写 dispatchKeyEvent 方法**

在 `MainActivity` 类中添加 `dispatchKeyEvent` 方法：

```kotlin
override fun dispatchKeyEvent(event: KeyEvent): Boolean {
    if (VideoPlayerVolumeManager.hasActivePlayer()) {
        when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_MUTE -> {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    handleVolumeKey(event.keyCode)
                }
                return true
            }
        }
    }
    return super.dispatchKeyEvent(event)
}

private fun handleVolumeKey(keyCode: Int) {
    val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
    
    val newSystemVolume = when (keyCode) {
        KeyEvent.KEYCODE_VOLUME_UP -> (currentVolume + 1).coerceAtMost(maxVolume)
        KeyEvent.KEYCODE_VOLUME_DOWN -> (currentVolume - 1).coerceAtLeast(0)
        KeyEvent.KEYCODE_MUTE -> 0
        else -> currentVolume
    }
    
    audioManager.setStreamVolume(
        AudioManager.STREAM_MUSIC,
        newSystemVolume,
        AudioManager.FLAG_SHOW_UI
    )
    
    val playerVolume = if (maxVolume > 0) {
        newSystemVolume.toFloat() / maxVolume.toFloat()
    } else {
        1f
    }
    
    VideoPlayerVolumeManager.setVolume(playerVolume)
}
```

- [ ] **Step 4: 提交代码**

```bash
git add tv/src/main/java/top/yogiczy/mytv/tv/MainActivity.kt
git commit -m "feat: intercept volume keys to trigger fade effect"
```

---

### Task 4: 验证功能

- [ ] **Step 1: 编译项目**

```bash
cd /workspace && ./gradlew :tv:compileDebugKotlin
```

- [ ] **Step 2: 确认功能正常**

手动测试：
1. 启动应用并播放视频
2. 按手机音量键
3. 观察系统音量 UI 显示
4. 听到音量渐变效果（淡入淡出）

---

## 注意事项

1. **内存泄漏防护**：使用 `WeakReference` 存储播放器引用，避免内存泄漏
2. **多播放器场景**：当前设计只支持一个活跃播放器，多视窗模式下只有最后注册的播放器会响应音量键
3. **配置兼容**：淡入淡出效果仍然受 `videoPlayerEnableVolumeFade` 配置控制
