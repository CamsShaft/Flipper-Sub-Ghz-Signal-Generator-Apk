package com.subghz.signalgenerator.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.subghz.signalgenerator.core.*
import com.subghz.signalgenerator.ui.components.*
import com.subghz.signalgenerator.ui.theme.*

@Composable
fun InspectorScreen(
    timings: List<Int>,
    audioEngine: AudioEngine
) {
    var isPlaying by remember { mutableStateOf(false) }
    var playbackProgress by remember { mutableFloatStateOf(0f) }
    var timeScaleText by remember { mutableStateOf("50") }
    var carrierFreq by remember { mutableStateOf("800") }

    val stats = remember(timings) {
        SignalProcessor.analyzeTimings(timings)
    }

    val waveformData = remember(timings) {
        audioEngine.generateWaveformData(timings, 500)
    }

    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose { audioEngine.stop() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionHeader("Signal Inspector")

        if (timings.isEmpty()) {
            SubGhzCard {
                Text(
                    "No signal loaded. Generate a signal or load a file first.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
            return@Column
        }

        // -- Digital Waveform --
        SectionHeader("Digital Waveform")
        DigitalWaveform(
            timings = timings,
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
        )

        // -- Signal Statistics --
        SectionHeader("Signal Analysis")
        SubGhzCard {
            InfoRow("Total Pulses", "${stats.totalPulses}")
            InfoRow("High Pulses", "${stats.highPulses}", valueColor = GreenSignal)
            InfoRow("Low Pulses", "${stats.lowPulses}", valueColor = RedSignal)
            HorizontalDivider(color = BorderSubtle)
            InfoRow("Min Pulse", "${stats.minPulseUs} µs")
            InfoRow("Max Pulse", "${stats.maxPulseUs} µs")
            InfoRow("Avg Pulse", "${stats.avgPulseUs} µs")
            InfoRow("Avg High", "${stats.avgHighUs} µs", valueColor = GreenSignal)
            InfoRow("Avg Low", "${stats.avgLowUs} µs", valueColor = RedSignal)
            HorizontalDivider(color = BorderSubtle)
            InfoRow("Total Duration", "%.2f ms".format(stats.totalDurationMs))
            InfoRow("Est. Bitrate", "~${stats.estimatedBitrate} bps")
        }

        // -- Timing Distribution (single pass, no intermediate lists) --
        SectionHeader("Pulse Distribution")
        SubGhzCard {
            val highBuckets = mutableMapOf<String, Int>()
            val lowBuckets = mutableMapOf<String, Int>()
            for (t in timings) {
                val cat = categorizeUs(kotlin.math.abs(t))
                if (t > 0) highBuckets[cat] = (highBuckets[cat] ?: 0) + 1
                else lowBuckets[cat] = (lowBuckets[cat] ?: 0) + 1
            }

            if (highBuckets.isNotEmpty()) {
                Text("HIGH pulses:", style = MaterialTheme.typography.labelLarge, color = GreenSignal)
                highBuckets.toSortedMap().forEach { (range, count) ->
                    InfoRow("  $range", "${count}x", valueColor = GreenSignal)
                }
            }

            if (lowBuckets.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text("LOW pulses:", style = MaterialTheme.typography.labelLarge, color = RedSignal)
                lowBuckets.toSortedMap().forEach { (range, count) ->
                    InfoRow("  $range", "${count}x", valueColor = RedSignal)
                }
            }
        }

        // -- Audio Inspector --
        SectionHeader("Audio Inspector")
        SubGhzCard {
            AudioWaveform(
                waveformData = waveformData,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                playbackProgress = playbackProgress
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SubGhzTextField(
                    value = timeScaleText,
                    onValueChange = { timeScaleText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = "Time Scale",
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f)
                )
                SubGhzTextField(
                    value = carrierFreq,
                    onValueChange = { carrierFreq = it },
                    label = "Carrier Hz",
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f)
                )
            }

            Text(
                "Time scale: higher = slower playback, more detail. " +
                "Carrier frequency controls the audible tone pitch.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SubGhzButton(
                    text = if (isPlaying) "Stop" else "Play Signal",
                    onClick = {
                        if (isPlaying) {
                            audioEngine.stop()
                            isPlaying = false
                            playbackProgress = 0f
                        } else {
                            isPlaying = true
                            playbackProgress = 0f
                            val freq = carrierFreq.toDoubleOrNull() ?: 800.0
                            val timeScale = timeScaleText.toFloatOrNull()?.coerceIn(1f, 500f) ?: 50f
                            audioEngine.play(
                                timings = timings,
                                timeScale = timeScale,
                                onComplete = {
                                    isPlaying = false
                                    playbackProgress = 1f
                                }
                            )
                        }
                    },
                    modifier = Modifier.weight(1f),
                    isPrimary = !isPlaying
                )
            }
        }

        // -- Raw Timing Data --
        SectionHeader(
            title = "Raw Timings",
            action = {
                Text(
                    "${timings.size} values",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        )
        SubGhzCard {
            val displayTimings = timings.take(200)
            Text(
                displayTimings.joinToString(" ") +
                    if (timings.size > 200) " ... (${timings.size - 200} more)" else "",
                style = MaterialTheme.typography.labelMedium,
                color = CyanAccent
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

private fun categorizeUs(us: Int): String {
    return when {
        us < 200 -> "0-200µs"
        us < 500 -> "200-500µs"
        us < 1000 -> "500-1000µs"
        us < 2000 -> "1-2ms"
        us < 5000 -> "2-5ms"
        us < 10000 -> "5-10ms"
        else -> "10ms+"
    }
}
