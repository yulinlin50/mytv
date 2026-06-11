package top.yogiczy.mytv.tv

import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import top.yogiczy.mytv.tv.ui.App
import top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.new.VideoPlayerVolumeManager
import top.yogiczy.mytv.tv.ui.theme.MyTvTheme
import top.yogiczy.mytv.tv.ui.utils.Configs

class MainActivity : ComponentActivity() {
    private val audioManager: AudioManager by lazy {
        getSystemService(AUDIO_SERVICE) as AudioManager
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        setContent {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowCompat.getInsetsController(window, window.decorView).let { insetsController ->
                insetsController.hide(WindowInsetsCompat.Type.statusBars())
                insetsController.hide(WindowInsetsCompat.Type.navigationBars())
                insetsController.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }

            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

            MyTvTheme {
                App(
                    onBackPressed = {
                        finishAffinity()
                    },
                )
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && Configs.appPipEnable) {
            val aspectRatio = Rational(16, 9)
            val params = PictureInPictureParams.Builder().setAspectRatio(aspectRatio).build()
            enterPictureInPictureMode(params)
        }
    }

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
        
        val currentVolumeRatio = if (maxVolume > 0) {
            currentVolume.toFloat() / maxVolume.toFloat()
        } else {
            1f
        }
        VideoPlayerVolumeManager.syncVolume(currentVolumeRatio)
        
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
}
