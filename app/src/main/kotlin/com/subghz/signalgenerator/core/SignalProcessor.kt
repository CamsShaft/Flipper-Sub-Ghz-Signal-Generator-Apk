package com.subghz.signalgenerator.core

import kotlin.math.abs
import kotlin.math.min

object SignalProcessor {

    // Hard limits to prevent OOM and UI lag
    const val MAX_INPUT_BYTES = 524_288       // 512 KB max file input
    const val MAX_TIMINGS = 50_000            // max timing values kept in memory
    const val MAX_RENDER_TIMINGS = 4_000      // max timings passed to Canvas
    const val MAX_AUDIO_TIMINGS = 20_000      // max timings for audio synthesis
    const val MAX_RAW_TEXT_LENGTH = 1_000_000  // max chars for raw text parsing

    /**
     * Convert raw bytes to timing values with size cap.
     */
    fun bytesToTimings(bytes: ByteArray, minUs: Int, maxUs: Int): List<Int> {
        if (bytes.isEmpty()) return emptyList()
        val range = maxUs - minUs
        val limit = min(bytes.size, MAX_TIMINGS)
        var sign = 1
        return ArrayList<Int>(limit).apply {
            for (i in 0 until limit) {
                val u = bytes[i].toUByte().toInt()
                val us = minUs + (u * range / 255)
                add(sign * us)
                sign = -sign
            }
        }
    }

    fun timingsToBytes(timings: List<Int>, minUs: Int, maxUs: Int): ByteArray {
        val range = maxUs - minUs
        if (range == 0) return ByteArray(timings.size)
        return ByteArray(timings.size) { i ->
            val absVal = abs(timings[i])
            val byteVal = ((absVal - minUs) * 255 / range).coerceIn(0, 255)
            byteVal.toByte()
        }
    }

    fun hexToTimings(hex: String, minUs: Int, maxUs: Int): List<Int> {
        val cleaned = hex.take(MAX_RAW_TEXT_LENGTH)
            .replace("0x", "").replace(",", " ")
            .replace("\\s+".toRegex(), " ").trim()
        val bytes = if (cleaned.contains(" ")) {
            cleaned.split(" ").take(MAX_TIMINGS)
                .mapNotNull { it.toIntOrNull(16)?.toByte() }.toByteArray()
        } else {
            cleaned.chunked(2).take(MAX_TIMINGS)
                .mapNotNull { it.toIntOrNull(16)?.toByte() }.toByteArray()
        }
        return bytesToTimings(bytes, minUs, maxUs)
    }

    fun binaryStringToTimings(
        binary: String,
        shortUs: Int = 350,
        longUs: Int = 1050,
        encoding: SignalEncoding = SignalEncoding.PWM
    ): List<Int> {
        val bits = binary.take(MAX_RAW_TEXT_LENGTH).filter { it == '0' || it == '1' }
        val maxBits = MAX_TIMINGS / 2 // each bit produces ~2 timings
        val limitedBits = if (bits.length > maxBits) bits.substring(0, maxBits) else bits

        return when (encoding) {
            SignalEncoding.PWM -> {
                ArrayList<Int>(limitedBits.length * 2).apply {
                    for (bit in limitedBits) {
                        if (bit == '1') { add(longUs); add(-shortUs) }
                        else { add(shortUs); add(-longUs) }
                    }
                }
            }
            SignalEncoding.MANCHESTER -> {
                val hp = shortUs
                ArrayList<Int>(limitedBits.length * 2).apply {
                    for (bit in limitedBits) {
                        if (bit == '1') { add(-hp); add(hp) }
                        else { add(hp); add(-hp) }
                    }
                }
            }
            SignalEncoding.PPM -> {
                ArrayList<Int>(limitedBits.length * 2).apply {
                    for (bit in limitedBits) {
                        if (bit == '1') { add(shortUs); add(-longUs) }
                        else { add(shortUs); add(-shortUs) }
                    }
                }
            }
            SignalEncoding.NRZ -> {
                val timings = ArrayList<Int>()
                var i = 0
                while (i < limitedBits.length && timings.size < MAX_TIMINGS) {
                    var count = 1
                    while (i + count < limitedBits.length && limitedBits[i + count] == limitedBits[i]) count++
                    val duration = count * shortUs
                    timings.add(if (limitedBits[i] == '1') duration else -duration)
                    i += count
                }
                timings
            }
            SignalEncoding.RAW -> {
                var sign = 1
                ArrayList<Int>(limitedBits.length).apply {
                    for (bit in limitedBits) {
                        val us = if (bit == '1') longUs else shortUs
                        add(sign * us)
                        sign = -sign
                    }
                }
            }
        }
    }

    fun parseRawTimings(input: String): List<Int> {
        return input.take(MAX_RAW_TEXT_LENGTH)
            .replace(",", " ")
            .split("\\s+".toRegex())
            .asSequence()
            .mapNotNull { it.trim().toIntOrNull() }
            .take(MAX_TIMINGS)
            .toList()
    }

    /**
     * Downsample timings for rendering. Merges adjacent timings when
     * there are too many to draw efficiently.
     */
    fun downsampleForRender(timings: List<Int>, maxPoints: Int = MAX_RENDER_TIMINGS): List<Int> {
        if (timings.size <= maxPoints) return timings

        val ratio = timings.size.toFloat() / maxPoints
        val result = ArrayList<Int>(maxPoints)
        var i = 0f
        while (i < timings.size && result.size < maxPoints) {
            val idx = i.toInt().coerceIn(0, timings.lastIndex)
            result.add(timings[idx])
            i += ratio
        }
        return result
    }

    /**
     * Single-pass signal analysis — no intermediate list allocations.
     */
    fun analyzeTimings(timings: List<Int>): SignalStats {
        if (timings.isEmpty()) return SignalStats()

        var totalPulses = 0
        var highCount = 0
        var lowCount = 0
        var minPulse = Int.MAX_VALUE
        var maxPulse = Int.MIN_VALUE
        var totalUs = 0L
        var highSum = 0L
        var lowSum = 0L

        for (t in timings) {
            totalPulses++
            val a = abs(t)
            totalUs += a
            if (a < minPulse) minPulse = a
            if (a > maxPulse) maxPulse = a
            if (t > 0) { highCount++; highSum += a }
            else { lowCount++; lowSum += a }
        }

        val avgPulse = if (totalPulses > 0) (totalUs / totalPulses).toInt() else 0
        val avgHigh = if (highCount > 0) (highSum / highCount).toInt() else 0
        val avgLow = if (lowCount > 0) (lowSum / lowCount).toInt() else 0
        val bitrate = if (avgPulse > 0) (1_000_000.0 / (avgPulse * 2)).toInt() else 0

        return SignalStats(
            totalPulses = totalPulses,
            highPulses = highCount,
            lowPulses = lowCount,
            minPulseUs = if (minPulse == Int.MAX_VALUE) 0 else minPulse,
            maxPulseUs = if (maxPulse == Int.MIN_VALUE) 0 else maxPulse,
            avgPulseUs = avgPulse,
            totalDurationUs = totalUs,
            totalDurationMs = totalUs / 1000.0,
            avgHighUs = avgHigh,
            avgLowUs = avgLow,
            estimatedBitrate = bitrate
        )
    }

    /**
     * Check if input exceeds limits and return a truncation message, or null if fine.
     */
    fun checkLimits(byteCount: Int? = null, timingCount: Int? = null): String? {
        if (byteCount != null && byteCount > MAX_INPUT_BYTES) {
            return "File truncated: ${byteCount / 1024}KB exceeds ${MAX_INPUT_BYTES / 1024}KB limit. " +
                   "First ${MAX_INPUT_BYTES / 1024}KB used."
        }
        if (timingCount != null && timingCount > MAX_TIMINGS) {
            return "Signal truncated to $MAX_TIMINGS timings (had $timingCount)."
        }
        return null
    }
}

data class SignalStats(
    val totalPulses: Int = 0,
    val highPulses: Int = 0,
    val lowPulses: Int = 0,
    val minPulseUs: Int = 0,
    val maxPulseUs: Int = 0,
    val avgPulseUs: Int = 0,
    val totalDurationUs: Long = 0L,
    val totalDurationMs: Double = 0.0,
    val avgHighUs: Int = 0,
    val avgLowUs: Int = 0,
    val estimatedBitrate: Int = 0
)
