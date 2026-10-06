package com.promptflow.app.core.model

/**
 * 声音美化（美音大师）预设模型
 *
 * 针对不同口播、朗读与播客场景定制的声学 DSP 调校参数
 */
enum class VoiceFilter(
    val id: String,
    val displayName: String,
    val tag: String,
    val description: String,
    val accentColorHex: Long,
    val bassBoostMb: Short,       // 低频共鸣增益 (120Hz-250Hz, 单位 millibels, 100mb = 1dB)
    val clarityBoostMb: Short,    // 中高频咬字清晰度增益 (3kHz-5kHz)
    val enableNoiseSuppressor: Boolean, // 智能底噪消除
    val enableGainControl: Boolean,     // 动态防爆音与音量均衡
    val reverbPreset: Short             // 录音棚微混响预设 (0: 无, 1: 小型棚, 2: 中型房间)
) {
    NATURAL(
        id = "natural",
        displayName = "原声直录",
        tag = "真实",
        description = "保留真实人声细节，无任何音效渲染与声学处理",
        accentColorHex = 0xFF94A3B8,
        bassBoostMb = 0,
        clarityBoostMb = 0,
        enableNoiseSuppressor = false,
        enableGainControl = false,
        reverbPreset = 0
    ),
    RADIO_WARM(
        id = "radio_warm",
        displayName = "磁性电台",
        tag = "大师推荐",
        description = "加厚 150Hz 胸腔共鸣，呈现播音主持级低沉浑厚的磁性嗓音",
        accentColorHex = 0xFFF59E0B, // 暖金色
        bassBoostMb = 600,            // +6 dB 低音暖化
        clarityBoostMb = 200,         // +2 dB 基础清晰度
        enableNoiseSuppressor = true,
        enableGainControl = true,
        reverbPreset = 1              // 微型录音棚声场
    ),
    STUDIO_CLEAN(
        id = "studio_clean",
        displayName = "清澈通透",
        tag = "高清晰度",
        description = "暴力滤除底噪并强化 4kHz 齿音咬字，使普通话表达干脆立挺",
        accentColorHex = 0xFF06B6D4, // 青翠通透色
        bassBoostMb = 100,
        clarityBoostMb = 700,         // +7 dB 咬字提亮
        enableNoiseSuppressor = true,
        enableGainControl = true,
        reverbPreset = 0
    ),
    INTIMATE_PODCAST(
        id = "podcast",
        displayName = "治愈播客",
        tag = "微混响",
        description = "注入温润微空间混响与动态防爆音，娓娓道来，亲切自然",
        accentColorHex = 0xFFEC4899, // 柔粉暖色
        bassBoostMb = 350,
        clarityBoostMb = 300,
        enableNoiseSuppressor = true,
        enableGainControl = true,
        reverbPreset = 2              // 温润微混响
    ),
    DEEP_DENOISE(
        id = "deep_denoise",
        displayName = "纯净降噪",
        tag = "降噪MAX",
        description = "深度滤除风扇、空调底噪与窗外车流声，仅萃取清晰人声",
        accentColorHex = 0xFF10B981, // 翡翠绿
        bassBoostMb = 0,
        clarityBoostMb = 200,
        enableNoiseSuppressor = true,
        enableGainControl = true,
        reverbPreset = 0
    );

    companion object {
        val default = RADIO_WARM
    }
}
