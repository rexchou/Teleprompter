package com.promptflow.app.domain.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * High precision coroutine-based teleprompter scroll engine.
 */
class PrompterScrollEngine(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {

    private val _isScrolling = MutableStateFlow(false)
    val isScrolling: StateFlow<Boolean> = _isScrolling.asStateFlow()

    private val _scrollSpeed = MutableStateFlow(1.2f)
    val scrollSpeed: StateFlow<Float> = _scrollSpeed.asStateFlow()

    private val _scrollOffset = MutableStateFlow(0f)
    val scrollOffset: StateFlow<Float> = _scrollOffset.asStateFlow()

    private var tickerJob: Job? = null
    var maxScrollHeight: Float = 0f

    fun start() {
        if (_isScrolling.value) return
        _isScrolling.value = true

        tickerJob?.cancel()
        tickerJob = scope.launch {
            val frameIntervalMs = 16L // ~60 FPS
            while (isActive && _isScrolling.value) {
                val step = _scrollSpeed.value * 2.0f
                val next = _scrollOffset.value + step
                if (maxScrollHeight > 0f && next >= maxScrollHeight) {
                    _scrollOffset.value = maxScrollHeight
                    _isScrolling.value = false
                    break
                } else {
                    _scrollOffset.value = next
                }
                delay(frameIntervalMs)
            }
        }
    }

    fun pause() {
        _isScrolling.value = false
        tickerJob?.cancel()
        tickerJob = null
    }

    fun toggle() {
        if (_isScrolling.value) pause() else start()
    }

    fun setSpeed(speed: Float) {
        _scrollSpeed.value = speed.coerceIn(0.5f, 3.0f)
    }

    fun updateScrollOffset(newOffset: Float) {
        _scrollOffset.value = newOffset.coerceAtLeast(0f)
    }

    fun rewind(pixels: Float = 120f) {
        _scrollOffset.value = (_scrollOffset.value - pixels).coerceAtLeast(0f)
    }

    fun reset() {
        pause()
        _scrollOffset.value = 0f
    }
}
