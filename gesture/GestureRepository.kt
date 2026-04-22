package com.ringhud.gesture

import com.ringhud.ble.BleConnectionManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GestureRepository @Inject constructor(
    private val bleManager: BleConnectionManager
) {
    private val scope      = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val normalizer = SignalNormalizer()
    private val buffer     = WindowBuffer()

    private val _gestures = MutableSharedFlow<GestureEvent>(
        replay     = 0,
        extraBufferCapacity = 8
    )

    /**
     * Hot flow of recognised gestures.
     * Collect in ViewModel with debounce to ignore noise bursts.
     */
    val gestures: SharedFlow<GestureEvent> = _gestures.asSharedFlow()

    /** Raw normalised frames — useful for debug overlays. */
    val normFrames: StateFlow<List<NormFrame>> = buffer.flow

    init {
        scope.launch {
            bleManager.frames
                .collect { raw ->
                    val norm = normalizer.normalize(raw)
                    buffer.push(norm)
                    val window = buffer.flow.value
                    PatternMatcher.classify(window)?.let { event ->
                        _gestures.emit(event)
                    }
                }
        }
    }
}
