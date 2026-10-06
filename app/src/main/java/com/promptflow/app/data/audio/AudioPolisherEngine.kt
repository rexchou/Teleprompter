package com.promptflow.app.data.audio

import android.media.MediaPlayer
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.Equalizer
import android.media.audiofx.NoiseSuppressor
import android.media.audiofx.PresetReverb
import android.util.Log
import com.promptflow.app.core.model.VoiceFilter

/**
 * 声音美化引擎 (Audio Polisher Engine)
 *
 * 基于 Android 原生 DSP AudioEffect 系统实现 0 延迟、0 云端成本的端侧专业级音效润色：
 * 1. 5段/10段 Equalizer：调节低频胸腔共鸣 (120-250Hz) 与中高频咬字清晰度 (3k-5kHz)
 * 2. NoiseSuppressor：硬件级背景底噪抑制
 * 3. AutomaticGainControl：广播级自动响度拉平与防爆音
 * 4. PresetReverb：微空间录音棚混响
 * 5. A/B 旁路监听系统：支持一键“按住对比原声”
 */
class AudioPolisherEngine {

    private val tag = "AudioPolisherEngine"

    private var equalizer: Equalizer? = null
    private var presetReverb: PresetReverb? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var automaticGainControl: AutomaticGainControl? = null

    private var currentFilter: VoiceFilter = VoiceFilter.default
    private var isBypassed: Boolean = false
    private var currentAudioSessionId: Int = 0

    /**
     * 将美音引擎挂载到播放器 AudioSession 上
     */
    fun attachToSession(audioSessionId: Int) {
        if (audioSessionId == 0) return
        release()
        currentAudioSessionId = audioSessionId

        try {
            // 1. 初始化均衡器 (Equalizer)
            equalizer = Equalizer(0, audioSessionId).apply {
                enabled = true
            }

            // 2. 初始化微混响 (PresetReverb)
            presetReverb = PresetReverb(0, audioSessionId).apply {
                enabled = true
            }

            // 3. 硬件级降噪 (如设备支持)
            if (NoiseSuppressor.isAvailable()) {
                try {
                    noiseSuppressor = NoiseSuppressor.create(audioSessionId)?.apply {
                        enabled = true
                    }
                } catch (e: Exception) {
                    Log.w(tag, "NoiseSuppressor not supported on this session", e)
                }
            }

            // 4. 自动增益控制 (如设备支持)
            if (AutomaticGainControl.isAvailable()) {
                try {
                    automaticGainControl = AutomaticGainControl.create(audioSessionId)?.apply {
                        enabled = true
                    }
                } catch (e: Exception) {
                    Log.w(tag, "AutomaticGainControl not supported on this session", e)
                }
            }

            // 应用当前预设
            applyFilter(currentFilter)
        } catch (e: Exception) {
            Log.e(tag, "Failed to initialize AudioPolisher effects: ${e.message}", e)
        }
    }

    /**
     * 应用指定的美音预设
     */
    fun applyFilter(filter: VoiceFilter) {
        currentFilter = filter
        if (isBypassed) return

        val eq = equalizer ?: return
        try {
            if (filter == VoiceFilter.NATURAL) {
                // 原声：全部频段归零，关闭特殊效果
                for (band in 0 until eq.numberOfBands) {
                    eq.setBandLevel(band.toShort(), 0)
                }
                presetReverb?.preset = PresetReverb.PRESET_NONE
                noiseSuppressor?.enabled = false
                automaticGainControl?.enabled = false
                return
            }

            // 针对频段进行调校
            val numBands = eq.numberOfBands
            val bandRange = eq.bandLevelRange // e.g. [-1500, 1500] millibels (-15dB to +15dB)
            val minLevel = bandRange[0]
            val maxLevel = bandRange[1]

            for (i in 0 until numBands) {
                val centerFreq = eq.getCenterFreq(i.toShort()) / 1000 // in Hz
                val level = when {
                    // 低频段 (60Hz ~ 250Hz)：胸腔共鸣提升
                    centerFreq in 50..300 -> {
                        filter.bassBoostMb.coerceIn(minLevel, maxLevel)
                    }
                    // 中频段 (500Hz ~ 2kHz)：保持自然人声平衡
                    centerFreq in 400..2000 -> {
                        0.toShort()
                    }
                    // 高频段 (3kHz ~ 6kHz)：齿音与咬字清晰度提亮
                    centerFreq in 2500..8000 -> {
                        filter.clarityBoostMb.coerceIn(minLevel, maxLevel)
                    }
                    else -> 0.toShort()
                }
                eq.setBandLevel(i.toShort(), level)
            }

            // 录音棚空间微混响
            presetReverb?.let { reverb ->
                reverb.preset = when (filter.reverbPreset.toInt()) {
                    1 -> PresetReverb.PRESET_SMALLROOM
                    2 -> PresetReverb.PRESET_MEDIUMROOM
                    else -> PresetReverb.PRESET_NONE
                }
            }

            // 降噪与动态均衡
            noiseSuppressor?.enabled = filter.enableNoiseSuppressor
            automaticGainControl?.enabled = filter.enableGainControl

        } catch (e: Exception) {
            Log.e(tag, "Error applying voice filter: ${e.message}", e)
        }
    }

    /**
     * 一键旁路控制（用于“按住对比原声”瞬时切换）
     * @param bypass true 时瞬间直通原声；false 时恢复美音效果
     */
    fun setBypass(bypass: Boolean) {
        if (isBypassed == bypass) return
        isBypassed = bypass

        if (bypass) {
            // 旁路直通：清空均衡器并关闭特效
            equalizer?.let { eq ->
                for (b in 0 until eq.numberOfBands) {
                    try { eq.setBandLevel(b.toShort(), 0) } catch (_: Exception) {}
                }
            }
            try { presetReverb?.preset = PresetReverb.PRESET_NONE } catch (_: Exception) {}
            try { noiseSuppressor?.enabled = false } catch (_: Exception) {}
            try { automaticGainControl?.enabled = false } catch (_: Exception) {}
        } else {
            // 恢复美音
            applyFilter(currentFilter)
        }
    }

    fun release() {
        try { equalizer?.release() } catch (_: Exception) {}
        try { presetReverb?.release() } catch (_: Exception) {}
        try { noiseSuppressor?.release() } catch (_: Exception) {}
        try { automaticGainControl?.release() } catch (_: Exception) {}

        equalizer = null
        presetReverb = null
        noiseSuppressor = null
        automaticGainControl = null
        currentAudioSessionId = 0
    }
}
