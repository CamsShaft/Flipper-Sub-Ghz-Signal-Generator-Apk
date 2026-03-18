package com.subghz.signalgenerator.core

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

class AudioEngine {
    companion object {
        const val SAMPLE_RATE = 44100
        private const val CARRIER_FREQ = 800.0
        private const val TWO_PI = 2.0 * Math.PI
        private const val MAX_SECONDS = 15   // cap playback duration
    }

    private var audioTrack: AudioTrack? = null
    @Volatile
    var isPlaying = false
        private set

    fun timingsToSamples(
        timings: List<Int>,
        carrierFreq: Double = CARRIER_FREQ,
        timeScale: Float = 1.0f
    ): ShortArray {
        if (timings.isEmpty()) return ShortArray(0)

        // Cap the timings we actually process
        val cappedTimings = if (timings.size > SignalProcessor.MAX_AUDIO_TIMINGS)
            timings.subList(0, SignalProcessor.MAX_AUDIO_TIMINGS)
        else timings

        val totalUs = cappedTimings.sumOf { abs(it) }.toLong()
        val totalSeconds = (totalUs * timeScale) / 1_000_000.0
        val clampedSeconds = totalSeconds.coerceAtMost(MAX_SECONDS.toDouble())
        val totalSamples = (clampedSeconds * SAMPLE_RATE).toInt()

        if (totalSamples <= 0) return ShortArray(0)

        val samples = ShortArray(totalSamples)
        var sampleIndex = 0
        var phase = 0.0
        val phaseIncrement = TWO_PI * carrierFreq / SAMPLE_RATE

        for (timing in cappedTimings) {
            val durationUs = abs(timing)
            val durationSamples = ((durationUs * timeScale) / 1_000_000.0 * SAMPLE_RATE).toInt()
            val isHigh = timing > 0

            for (i in 0 until durationSamples) {
                if (sampleIndex >= totalSamples) break
                if (isHigh) {
                    samples[sampleIndex] = (sin(phase) * Short.MAX_VALUE * 0.8).toInt().toShort()
                    phase += phaseIncrement
                } else {
                    samples[sampleIndex] = 0
                }
                sampleIndex++
            }
            if (sampleIndex >= totalSamples) break
        }

        return samples
    }

    fun generateWaveformData(timings: List<Int>, targetPoints: Int = 500): FloatArray {
        if (timings.isEmpty()) return FloatArray(0)

        // Use downsampled timings for efficiency
        val working = if (timings.size > SignalProcessor.MAX_AUDIO_TIMINGS)
            timings.subList(0, SignalProcessor.MAX_AUDIO_TIMINGS)
        else timings

        val totalUs = working.sumOf { abs(it) }.toFloat()
        if (totalUs == 0f) return FloatArray(0)

        val points = FloatArray(targetPoints)
        val usPerPoint = totalUs / targetPoints

        var accumulatedUs = 0f
        var timingIdx = 0

        for (p in 0 until targetPoints) {
            val targetUs = p * usPerPoint

            while (timingIdx < working.size &&
                   accumulatedUs + abs(working[timingIdx]) <= targetUs) {
                accumulatedUs += abs(working[timingIdx])
                timingIdx++
            }

            points[p] = if (timingIdx < working.size && working[timingIdx] > 0) 1f else 0f
        }

        return points
    }

    fun play(timings: List<Int>, timeScale: Float = 1.0f, onComplete: () -> Unit = {}) {
        stop()

        val samples = timingsToSamples(timings, timeScale = timeScale)
        if (samples.isEmpty()) {
            onComplete()
            return
        }

        val minBuf = AudioTrack.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = (samples.size * 2).coerceAtLeast(minBuf)

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        audioTrack?.let { track ->
            track.write(samples, 0, samples.size)
            track.setNotificationMarkerPosition(samples.size)
            track.setPlaybackPositionUpdateListener(object :
                AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(t: AudioTrack?) {
                    isPlaying = false
                    onComplete()
                }
                override fun onPeriodicNotification(t: AudioTrack?) {}
            })
            isPlaying = true
            track.play()
        }
    }

    fun stop() {
        isPlaying = false
        audioTrack?.let { track ->
            try {
                if (track.state == AudioTrack.STATE_INITIALIZED) {
                    track.stop()
                }
                track.release()
            } catch (_: Exception) {}
        }
        audioTrack = null
    }
}
