package com.subghz.signalgenerator.core

/**
 * Flipper Zero .sub file format support.
 * Handles RAW signal encoding and protocol-based generation.
 */

// Common sub-GHz frequencies (Hz)
enum class SubGhzFrequency(val hz: Long, val label: String) {
    F_300_00(300000000L, "300.00 MHz"),
    F_303_87(303875000L, "303.87 MHz"),
    F_310_00(310000000L, "310.00 MHz"),
    F_315_00(315000000L, "315.00 MHz"),
    F_318_00(318000000L, "318.00 MHz"),
    F_390_00(390000000L, "390.00 MHz"),
    F_418_00(418000000L, "418.00 MHz"),
    F_433_07(433070000L, "433.07 MHz"),
    F_433_42(433420000L, "433.42 MHz"),
    F_433_92(433920000L, "433.92 MHz"),
    F_434_77(434775000L, "434.77 MHz"),
    F_438_90(438900000L, "438.90 MHz"),
    F_868_35(868350000L, "868.35 MHz"),
    F_868_86(868860000L, "868.86 MHz"),
    F_915_00(915000000L, "915.00 MHz"),
    F_925_00(925000000L, "925.00 MHz"),
    CUSTOM(0L, "Custom");

    companion object {
        val DEFAULT = F_433_92
    }
}

// Flipper radio presets
enum class SubGhzPreset(val value: String, val label: String) {
    OOK_270("FuriHalSubGhzPresetOok270Async", "AM270 (OOK 270kHz)"),
    OOK_650("FuriHalSubGhzPresetOok650Async", "AM650 (OOK 650kHz)"),
    TWOFSK_DEV238("FuriHalSubGhzPreset2FSKDev238Async", "FM238 (2-FSK 2.38kHz)"),
    TWOFSK_DEV476("FuriHalSubGhzPreset2FSKDev476Async", "FM476 (2-FSK 4.76kHz)"),
    MSK_99_97("FuriHalSubGhzPresetMSK99_97KbAsync", "MSK (99.97Kb)"),
    GFSK_9_99("FuriHalSubGhzPresetGFSK9_99KbAsync", "GFSK (9.99Kb)"),
    CUSTOM("FuriHalSubGhzPresetCustom", "Custom");

    companion object {
        val DEFAULT = OOK_650
    }
}

// Signal encoding types
enum class SignalEncoding(val label: String) {
    RAW("RAW Timings"),
    MANCHESTER("Manchester"),
    PWM("PWM (Pulse Width)"),
    PPM("PPM (Pulse Position)"),
    NRZ("NRZ (Non-Return-to-Zero)")
}

// Protocol definitions for common devices
enum class SubGhzKnownProtocol(val label: String, val bitLength: Int, val encoding: SignalEncoding) {
    RAW("RAW Signal", 0, SignalEncoding.RAW),
    PRINCETON("Princeton (PT2262)", 24, SignalEncoding.PWM),
    CAME("CAME", 12, SignalEncoding.PWM),
    CAME_TWEE("CAME TWEE", 54, SignalEncoding.MANCHESTER),
    NICE_FLO("Nice FLO", 12, SignalEncoding.PWM),
    NICE_FLOR_S("Nice FLOR-S", 52, SignalEncoding.PWM),
    LINEAR("Linear", 10, SignalEncoding.PWM),
    GATE_TX("GateTX", 24, SignalEncoding.PWM),
    HOLTEK("Holtek HT12x", 12, SignalEncoding.PWM),
    KEELOQ("KeeLoq", 66, SignalEncoding.PWM),
    CHAMBERLAIN("Chamberlain", 9, SignalEncoding.PWM),
    SECURITY_PLUS_V1("Security+ 1.0", 21, SignalEncoding.PWM),
    SECURITY_PLUS_V2("Security+ 2.0", 40, SignalEncoding.PWM),
}

data class SubGhzSignal(
    val frequency: Long = SubGhzFrequency.DEFAULT.hz,
    val preset: SubGhzPreset = SubGhzPreset.DEFAULT,
    val protocol: SubGhzKnownProtocol = SubGhzKnownProtocol.RAW,
    val timings: List<Int> = emptyList(),
    val repeatCount: Int = 1,
    val gapUs: Int = 10000 // gap between repeats in µs
)

object SubFileGenerator {
    private const val MAX_RAW_LINE_VALUES = 512

    fun generate(signal: SubGhzSignal): String {
        val sb = StringBuilder()
        sb.appendLine("Filetype: Flipper SubGhz RAW File")
        sb.appendLine("Version: 1")
        sb.appendLine("Frequency: ${signal.frequency}")
        sb.appendLine("Preset: ${signal.preset.value}")
        sb.appendLine("Protocol: RAW")

        // Stream timings with repeats — no full list allocation
        var lineCount = 0
        val lineBuf = StringBuilder("RAW_Data:")

        fun flushLine() {
            if (lineCount > 0) {
                sb.appendLine(lineBuf.toString())
                lineBuf.clear()
                lineBuf.append("RAW_Data:")
                lineCount = 0
            }
        }

        fun appendTiming(value: Int) {
            lineBuf.append(' ').append(value)
            lineCount++
            if (lineCount >= MAX_RAW_LINE_VALUES) flushLine()
        }

        repeat(signal.repeatCount) { rep ->
            for (t in signal.timings) {
                appendTiming(t)
            }
            if (rep < signal.repeatCount - 1) {
                appendTiming(-signal.gapUs)
            }
        }
        flushLine()

        return sb.toString()
    }

    fun parseSubFile(content: String): SubGhzSignal? {
        val lines = content.lines()
        if (lines.firstOrNull()?.trim() != "Filetype: Flipper SubGhz RAW File") return null

        var frequency = SubGhzFrequency.DEFAULT.hz
        var preset = SubGhzPreset.DEFAULT
        val timings = mutableListOf<Int>()

        lines.forEach { line ->
            val trimmed = line.trim()
            when {
                trimmed.startsWith("Frequency:") -> {
                    frequency = trimmed.substringAfter(":").trim().toLongOrNull()
                        ?: SubGhzFrequency.DEFAULT.hz
                }
                trimmed.startsWith("Preset:") -> {
                    val presetStr = trimmed.substringAfter(":").trim()
                    preset = SubGhzPreset.entries.find { it.value == presetStr }
                        ?: SubGhzPreset.DEFAULT
                }
                trimmed.startsWith("RAW_Data:") -> {
                    val data = trimmed.substringAfter(":").trim()
                    data.split("\\s+".toRegex()).forEach { token ->
                        token.toIntOrNull()?.let { timings.add(it) }
                    }
                }
            }
        }

        return SubGhzSignal(
            frequency = frequency,
            preset = preset,
            timings = timings
        )
    }
}

// Protocol-specific signal generators
object ProtocolEncoder {

    fun encodePrinceton(
        code: Long,
        bitLength: Int = 24,
        pulseUs: Int = 350,
        repeatCount: Int = 10
    ): List<Int> {
        val timings = mutableListOf<Int>()
        // Princeton: short-long = 0, long-short = 1
        for (i in (bitLength - 1) downTo 0) {
            val bit = (code shr i) and 1L
            if (bit == 1L) {
                timings.add(pulseUs * 3)  // high long
                timings.add(-pulseUs)     // low short
            } else {
                timings.add(pulseUs)      // high short
                timings.add(-pulseUs * 3) // low long
            }
        }
        // Sync: 1 high, 31 low
        timings.add(pulseUs)
        timings.add(-pulseUs * 31)
        return timings
    }

    fun encodeCame(
        code: Long,
        bitLength: Int = 12,
        pulseUs: Int = 320,
        repeatCount: Int = 10
    ): List<Int> {
        val timings = mutableListOf<Int>()
        // Start bit
        timings.add(pulseUs)
        timings.add(-pulseUs * 31)
        // Data bits: short-long = 0, long-short = 1
        for (i in (bitLength - 1) downTo 0) {
            val bit = (code shr i) and 1L
            if (bit == 1L) {
                timings.add(pulseUs * 2)
                timings.add(-pulseUs)
            } else {
                timings.add(pulseUs)
                timings.add(-pulseUs * 2)
            }
        }
        return timings
    }

    fun encodeNiceFlo(
        code: Long,
        bitLength: Int = 12,
        pulseUs: Int = 700,
        repeatCount: Int = 10
    ): List<Int> {
        val timings = mutableListOf<Int>()
        // Preamble
        timings.add(pulseUs)
        timings.add(-pulseUs * 2)
        // Data bits
        for (i in (bitLength - 1) downTo 0) {
            val bit = (code shr i) and 1L
            if (bit == 1L) {
                timings.add(pulseUs * 2)
                timings.add(-pulseUs)
            } else {
                timings.add(pulseUs)
                timings.add(-pulseUs * 2)
            }
        }
        return timings
    }

    fun encodeLinear(
        code: Long,
        bitLength: Int = 10,
        pulseUs: Int = 500
    ): List<Int> {
        val timings = mutableListOf<Int>()
        for (i in (bitLength - 1) downTo 0) {
            val bit = (code shr i) and 1L
            if (bit == 1L) {
                timings.add(pulseUs * 3)
                timings.add(-pulseUs)
            } else {
                timings.add(pulseUs)
                timings.add(-pulseUs * 3)
            }
        }
        // Sync
        timings.add(pulseUs)
        timings.add(-pulseUs * 39)
        return timings
    }

    fun encodeManchester(
        data: ByteArray,
        bitPeriodUs: Int = 500
    ): List<Int> {
        val halfPeriod = bitPeriodUs / 2
        val maxBytes = SignalProcessor.MAX_TIMINGS / 16 // 16 timings per byte
        val limited = if (data.size > maxBytes) data.copyOf(maxBytes) else data
        val timings = ArrayList<Int>(limited.size * 16)
        for (byte in limited) {
            for (bit in 7 downTo 0) {
                val b = (byte.toInt() shr bit) and 1
                if (b == 1) {
                    timings.add(-halfPeriod)
                    timings.add(halfPeriod)
                } else {
                    timings.add(halfPeriod)
                    timings.add(-halfPeriod)
                }
            }
        }
        return timings
    }

    fun encodePwm(
        data: ByteArray,
        shortUs: Int = 350,
        longUs: Int = 1050
    ): List<Int> {
        val maxBytes = SignalProcessor.MAX_TIMINGS / 16
        val limited = if (data.size > maxBytes) data.copyOf(maxBytes) else data
        val timings = ArrayList<Int>(limited.size * 16)
        for (byte in limited) {
            for (bit in 7 downTo 0) {
                val b = (byte.toInt() shr bit) and 1
                if (b == 1) {
                    timings.add(longUs)
                    timings.add(-shortUs)
                } else {
                    timings.add(shortUs)
                    timings.add(-longUs)
                }
            }
        }
        return timings
    }
}
