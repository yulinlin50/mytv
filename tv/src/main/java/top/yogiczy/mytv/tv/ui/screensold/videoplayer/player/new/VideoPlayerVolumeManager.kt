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
    
    fun syncVolume(volume: Float) {
        currentPlayerRef?.get()?.syncVolume(volume)
    }
    
    fun hasActivePlayer(): Boolean {
        return currentPlayerRef?.get() != null
    }
}
