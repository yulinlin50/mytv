package top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.new

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.media3.common.text.Cue

class VideoPlayerStateManager(
    private val coroutineScope: CoroutineScope
) : IVideoPlayerState {
    
    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    
    private val _isBuffering = MutableStateFlow(false)
    override val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    override val error: StateFlow<String?> = _error.asStateFlow()
    
    private val _duration = MutableStateFlow(0L)
    override val duration: StateFlow<Long> = _duration.asStateFlow()
    
    private val _currentPosition = MutableStateFlow(0L)
    override val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()
    
    private val _metadata = MutableStateFlow(PlayerMetadata())
    override val metadata: StateFlow<PlayerMetadata> = _metadata.asStateFlow()
    
    private val _isPlaybackMode = MutableStateFlow(false)
    override val isPlaybackMode: StateFlow<Boolean> = _isPlaybackMode.asStateFlow()
    
    private val _playbackTimeRange = MutableStateFlow<Pair<Long, Long>?>(null)
    override val playbackTimeRange: StateFlow<Pair<Long, Long>?> = _playbackTimeRange.asStateFlow()
    
    private val _volume = MutableStateFlow(1f)
    override val volume: StateFlow<Float> = _volume.asStateFlow()
    
    private val _cues = MutableStateFlow<List<Cue>>(emptyList())
    val cues: StateFlow<List<Cue>> = _cues.asStateFlow()
    
    fun updateIsPlaying(value: Boolean) {
        coroutineScope.launch(Dispatchers.Main) {
            _isPlaying.value = value
        }
    }
    
    fun updateIsBuffering(value: Boolean) {
        coroutineScope.launch(Dispatchers.Main) {
            _isBuffering.value = value
        }
    }
    
    fun updateError(value: String?) {
        coroutineScope.launch(Dispatchers.Main) {
            _error.value = value
        }
    }
    
    fun updateDuration(value: Long) {
        coroutineScope.launch(Dispatchers.Main) {
            _duration.value = value
        }
    }
    
    fun updateCurrentPosition(value: Long) {
        coroutineScope.launch(Dispatchers.Main) {
            _currentPosition.value = value
        }
    }
    
    fun updateMetadata(value: PlayerMetadata) {
        coroutineScope.launch(Dispatchers.Main) {
            _metadata.value = value
        }
    }
    
    fun updatePlaybackMode(isPlayback: Boolean, startTime: Long, endTime: Long) {
        coroutineScope.launch(Dispatchers.Main) {
            _isPlaybackMode.value = isPlayback
            _playbackTimeRange.value = if (isPlayback && startTime > 0 && endTime > 0) {
                Pair(startTime, endTime)
            } else {
                null
            }
        }
    }
    
    fun updateVolume(value: Float) {
        coroutineScope.launch(Dispatchers.Main) {
            _volume.value = value
        }
    }
    
    fun updateCues(value: List<Cue>) {
        coroutineScope.launch(Dispatchers.Main) {
            _cues.value = value
        }
    }

    fun reset() {
        coroutineScope.launch(Dispatchers.Main) {
            _isPlaying.value = false
            _isBuffering.value = false
            _error.value = null
            _duration.value = 0L
            _currentPosition.value = 0L
            _metadata.value = PlayerMetadata()
            _isPlaybackMode.value = false
            _playbackTimeRange.value = null
            _cues.value = emptyList()
        }
    }
}
