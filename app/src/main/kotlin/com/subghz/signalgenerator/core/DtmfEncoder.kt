package com.subghz.signalgenerator.core

import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * DTMF (Dual-Tone Multi-Frequency) encoder.
 *
 * Generates OOK timing patterns that represent DTMF tones when
 * AM-modulated onto a sub-GHz carrier. Each key press produces two
 * simultaneous sine waves sampled and converted to on/off pulse timings.
 *
 * Standard DTMF frequency matrix:
 *            1209 Hz   1336 Hz   1477 Hz   1633 Hz
 *  697 Hz      1         2         3         A
 *  770 Hz      4         5         6         B
 *  852 Hz      7         8         9         C
 *  941 Hz      *         0         #         D
 */
object DtmfEncoder {

    private const val TWO_PI = 2.0 * Math.PI

    // Low frequency group (row tones)
    private val LOW_FREQ = mapOf(
        '1' to 697.0, '2' to 697.0, '3' to 697.0, 'A' to 697.0,
        '4' to 770.0, '5' to 770.0, '6' to 770.0, 'B' to 770.0,
        '7' to 852.0, '8' to 852.0, '9' to 852.0, 'C' to 852.0,
        '*' to 941.0, '0' to 941.0, '#' to 941.0, 'D' to 941.0
    )

    // High frequency group (column tones)
    private val HIGH_FREQ = mapOf(
        '1' to 1209.0, '4' to 1209.0, '7' to 1209.0, '*' to 1209.0,
        '2' to 1336.0, '5' to 1336.0, '8' to 1336.0, '0' to 1336.0,
        '3' to 1477.0, '6' to 1477.0, '9' to 1477.0, '#' to 1477.0,
        'A' to 1633.0, 'B' to 1633.0, 'C' to 1633.0, 'D' to 1633.0
    )

    // Valid DTMF characters
    val VALID_CHARS = setOf('0','1','2','3','4','5','6','7','8','9','*','#','A','B','C','D')

    // Standard keypad layout for UI
    val KEYPAD_ROWS = listOf(
        listOf('1', '2', '3', 'A'),
        listOf('4', '5', '6', 'B'),
        listOf('7', '8', '9', 'C'),
        listOf('*', '0', '#', 'D')
    )

    /**
     * Encode a DTMF digit sequence into OOK timing data.
     *
     * The combined two-tone waveform is sampled at [sampleRateHz] and
     * converted to on/off pulse timings based on zero-crossing detection.
     * Positive sum = carrier ON, negative/zero = carrier OFF.
     *
     * @param sequence  String of DTMF characters (0-9, A-D, *, #)
     * @param toneMs    Duration of each tone in milliseconds (ITU standard: 50-100ms)
     * @param pauseMs   Silence between tones in milliseconds (ITU standard: 50-100ms)
     * @param sampleRateHz  Sample rate for waveform generation (higher = more timing detail)
     */
    fun encode(
        sequence: String,
        toneMs: Int = 100,
        pauseMs: Int = 100,
        sampleRateHz: Int = 8000
    ): List<Int> {
        val chars = sequence.uppercase().filter { it in VALID_CHARS }
        if (chars.isEmpty()) return emptyList()

        val timings = ArrayList<Int>(chars.length * 200) // rough estimate
        val toneSamples = (sampleRateHz * toneMs) / 1000
        val pauseUs = pauseMs * 1000 // pause as single LOW timing

        for ((idx, ch) in chars.withIndex()) {
            val lowFreq = LOW_FREQ[ch] ?: continue
            val highFreq = HIGH_FREQ[ch] ?: continue

            // Generate combined two-tone waveform and convert to OOK timings
            val toneTimings = toneToTimings(lowFreq, highFreq, toneSamples, sampleRateHz)
            timings.addAll(toneTimings)

            // Add inter-digit pause (carrier OFF)
            if (idx < chars.length - 1) {
                timings.add(-pauseUs)
            }

            // Safety cap
            if (timings.size > SignalProcessor.MAX_TIMINGS) break
        }

        return timings
    }

    /**
     * Convert a single DTMF tone pair to OOK pulse timings.
     *
     * Samples the combined sine wave and groups consecutive positive/negative
     * samples into single timing values (microseconds).
     */
    private fun toneToTimings(
        lowFreq: Double,
        highFreq: Double,
        totalSamples: Int,
        sampleRate: Int
    ): List<Int> {
        val usPerSample = 1_000_000.0 / sampleRate
        val timings = ArrayList<Int>()

        var currentSign = 0 // 1=high, -1=low, 0=unset
        var accumulatedUs = 0.0

        for (i in 0 until totalSamples) {
            val t = i.toDouble() / sampleRate
            // Combined DTMF waveform (equal amplitude)
            val sample = sin(TWO_PI * lowFreq * t) + sin(TWO_PI * highFreq * t)
            val sign = if (sample >= 0.0) 1 else -1

            if (sign == currentSign) {
                accumulatedUs += usPerSample
            } else {
                // Flush accumulated pulse
                if (currentSign != 0 && accumulatedUs >= 1.0) {
                    val us = accumulatedUs.roundToInt()
                    timings.add(if (currentSign > 0) us else -us)
                }
                currentSign = sign
                accumulatedUs = usPerSample
            }
        }

        // Flush final pulse
        if (accumulatedUs >= 1.0) {
            val us = accumulatedUs.roundToInt()
            timings.add(if (currentSign > 0) us else -us)
        }

        return timings
    }

    /**
     * Get the frequency pair for a DTMF character.
     */
    fun getFrequencies(ch: Char): Pair<Double, Double>? {
        val upper = ch.uppercaseChar()
        val low = LOW_FREQ[upper] ?: return null
        val high = HIGH_FREQ[upper] ?: return null
        return low to high
    }

    /**
     * Get descriptive info about a DTMF sequence.
     */
    fun describeSequence(sequence: String): String {
        val chars = sequence.uppercase().filter { it in VALID_CHARS }
        if (chars.isEmpty()) return "No valid DTMF digits"
        return chars.map { ch ->
            val (low, high) = getFrequencies(ch) ?: return@map "$ch: ?"
            "$ch: ${low.toInt()}+${high.toInt()} Hz"
        }.joinToString("\n")
    }
}
