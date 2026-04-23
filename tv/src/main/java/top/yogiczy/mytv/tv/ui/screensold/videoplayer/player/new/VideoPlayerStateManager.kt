package top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.new

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import androidx.media3.common.text.Cue

class VideoPlayerStateManager(
    private val coroutineScope: CoroutineScope
) : IVideoPlayerState {
    
    private sealed class StateUpdate {
        data class IsPlaying(val value: Boolean) : StateUpdate()
        data class IsBuffering(val value: Boolean) : StateUpdate()
        data class Error(val value: String?) : StateUpdate()
        data class Duration(val value: Long) : StateUpdate()
        data class CurrentPosition(val value: Long) : StateUpdate()
        data class Metadata(val value: PlayerMetadata) : StateUpdate()
        data class PlaybackMode(val isPlayback: Boolean, val startTime: Long, val endTime: Long) : StateUpdate()
        data class Volume(val value: Float) : StateUpdate()
        data class Cues(val value: List<Cue>) : StateUpdate()
        object Reset : StateUpdate()
    }
    
    private val updateChannel = Channel<StateUpdate>(Channel.UNLIMITED)
    
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
    
    init {
        coroutineScope.launch(Dispatchers.Main) {
            updateChannel.consumeAsFlow().collect { update ->
                when (update) {
                    is StateUpdate.IsPlaying -> _isPlaying.value = update.value
                    is StateUpdate.IsBuffering -> _isBuffering.value = update.value
                    is StateUpdate.Error -> _error.value = update.value
                    is StateUpdate.Duration -> _duration.value = update.value
                    is StateUpdate.CurrentPosition -> _currentPosition.value = update.value
                    is StateUpdate.Metadata -> _metadata.value = update.value
                    is StateUpdate.PlaybackMode -> {
                        _isPlaybackMode.value = update.isPlayback
                        _playbackTimeRange.value = if (update.isPlayback && update.startTime > 0 && update.endTime > 0) {
                            Pair(update.startTime, update.endTime)
                        } else {
                            null
                        }
                    }
                    is StateUpdate.Volume -> _volume.value = update.value
                    is StateUpdate.Cues -> _cues.value = update.value
                    is StateUpdate.Reset -> {
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
        }
    }
    
    fun updateIsPlaying(value: Boolean) {
        updateChannel.trySend(StateUpdate.IsPlaying(value))
    }
    
    fun updateIsBuffering(value: Boolean) {
        updateChannel.trySend(StateUpdate.IsBuffering(value))
    }
    
    fun updateError(value: String?) {
        updateChannel.trySend(StateUpdate.Error(value))
    }
    
    fun updateDuration(value: Long) {
        updateChannel.trySend(StateUpdate.Duration(value))
    }
    
    fun updateCurrentPosition(value: Long) {
        updateChannel.trySend(StateUpdate.CurrentPosition(value))
    }
    
    fun updateMetadata(value: PlayerMetadata) {
        updateChannel.trySend(StateUpdate.Metadata(value))
    }
    
    fun updatePlaybackMode(isPlayback: Boolean, startTime: Long, endTime: Long) {
        updateChannel.trySend(StateUpdate.PlaybackMode(isPlayback, startTime, endTime))
    }
    
    fun updateVolume(value: Float) {
        updateChannel.trySend(StateUpdate.Volume(value))
    }
    
    fun updateCues(value: List<Cue>) {
        updateChannel.trySend(StateUpdate.Cues(value))
    }

    fun reset() {
        updateChannel.trySend(StateUpdate.Reset)
    }
}
