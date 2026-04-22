package com.ringhud.gesture

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Fixed-size sliding window of [NormFrame]s.
 *
 * At 50 Hz the default size of 50 = 1 second of signal.
 * Emits a new snapshot on every [push] without copying — the snapshot is an
 * immutable list view, so downstream collectors are safe from concurrent writes.
 *
 * Not thread-safe — call only from a single coroutine.
 */
class WindowBuffer(val size: Int = WINDOW_SIZE) {

    private val buf = ArrayDeque<NormFrame>(size)

    private val _flow = MutableStateFlow<List<NormFrame>>(emptyList())
    val flow: StateFlow<List<NormFrame>> = _flow.asStateFlow()

    fun push(frame: NormFrame) {
        if (buf.size == size) buf.removeFirst()
        buf.addLast(frame)
        _flow.value = buf.toList()   // shallow copy — NormFrame is immutable
    }

    fun clear() {
        buf.clear()
        _flow.value = emptyList()
    }

    val isFull: Boolean get() = buf.size == size

    companion object {
        const val WINDOW_SIZE = 50    // frames (@50 Hz = 1 s)
        const val SAMPLE_RATE_HZ = 50
    }
}
