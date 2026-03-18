package com.subghz.signalgenerator.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.subghz.signalgenerator.core.SignalProcessor
import com.subghz.signalgenerator.ui.theme.*
import kotlin.math.abs

@Composable
fun DigitalWaveform(
    timings: List<Int>,
    modifier: Modifier = Modifier,
    highColor: Color = GreenSignal,
    lowColor: Color = RedSignal,
    gridColor: Color = BorderSubtle,
    showGrid: Boolean = true,
    showMarkers: Boolean = true
) {
    if (timings.isEmpty()) {
        Box(
            modifier = modifier
                .background(SurfaceDark, RoundedCornerShape(8.dp))
                .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("No signal data", color = TextDisabled, style = MaterialTheme.typography.bodySmall)
        }
        return
    }

    // Downsample for rendering — prevents Canvas lag on large signals
    val renderTimings = remember(timings) {
        SignalProcessor.downsampleForRender(timings)
    }

    val isTruncated = timings.size > SignalProcessor.MAX_RENDER_TIMINGS

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(SurfaceDark, RoundedCornerShape(8.dp))
                .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp))
        ) {
            val w = size.width
            val h = size.height
            val padding = 12f
            val drawW = w - padding * 2
            val drawH = h - padding * 2

            val totalUs = renderTimings.sumOf { abs(it) }.toFloat()
            if (totalUs == 0f) return@Canvas

            val scale = drawW / totalUs
            val highY = padding + drawH * 0.15f
            val lowY = padding + drawH * 0.85f
            val midY = padding + drawH * 0.5f

            // Grid
            if (showGrid) {
                for (i in 0..4) {
                    val y = padding + drawH * i / 4f
                    drawLine(gridColor, Offset(padding, y), Offset(w - padding, y), strokeWidth = 0.5f)
                }
                val gridStepUs = (totalUs / 10).coerceAtLeast(1f)
                var gridUs = 0f
                while (gridUs < totalUs) {
                    val x = padding + gridUs * scale
                    if (x in padding..(w - padding)) {
                        drawLine(gridColor, Offset(x, padding), Offset(x, h - padding), strokeWidth = 0.5f)
                    }
                    gridUs += gridStepUs
                }
            }

            // Digital waveform — batch path + minimal drawRect calls
            val path = Path()
            var x = padding
            var isFirst = true

            // Only draw filled rects if count is reasonable for alpha blending
            val drawFill = renderTimings.size <= 2000

            for (timing in renderTimings) {
                val duration = abs(timing) * scale
                val y = if (timing > 0) highY else lowY

                if (isFirst) {
                    path.moveTo(x, y)
                    isFirst = false
                } else {
                    path.lineTo(x, y)
                }

                if (drawFill) {
                    drawRect(
                        color = (if (timing > 0) highColor else lowColor).copy(alpha = 0.15f),
                        topLeft = Offset(x, if (timing > 0) highY else midY),
                        size = Size(duration, if (timing > 0) midY - highY else lowY - midY)
                    )
                }

                path.lineTo(x + duration, y)
                x += duration
            }

            drawPath(path, color = GreenSignal, style = Stroke(width = 2f))

            // Center line
            drawLine(
                color = FlipperOrange.copy(alpha = 0.4f),
                start = Offset(padding, midY),
                end = Offset(w - padding, midY),
                strokeWidth = 1f
            )

            if (showMarkers) {
                drawCircle(highColor, 3f, Offset(padding - 2, highY))
                drawCircle(lowColor, 3f, Offset(padding - 2, lowY))
            }
        }

        if (isTruncated) {
            Text(
                "Showing downsampled preview (${renderTimings.size} of ${timings.size} pulses)",
                style = MaterialTheme.typography.bodySmall,
                color = AmberWarn,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
fun AudioWaveform(
    waveformData: FloatArray,
    modifier: Modifier = Modifier,
    playbackProgress: Float = 0f,
    color: Color = CyanAccent,
    progressColor: Color = FlipperOrange
) {
    if (waveformData.isEmpty()) {
        Box(
            modifier = modifier
                .background(SurfaceDark, RoundedCornerShape(8.dp))
                .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("No audio data", color = TextDisabled, style = MaterialTheme.typography.bodySmall)
        }
        return
    }

    Canvas(
        modifier = modifier
            .background(SurfaceDark, RoundedCornerShape(8.dp))
            .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp))
    ) {
        val w = size.width
        val h = size.height
        val padding = 8f
        val drawW = w - padding * 2
        val drawH = h - padding * 2
        val midY = h / 2f

        val barCount = waveformData.size.coerceAtMost(drawW.toInt())
        if (barCount <= 0) return@Canvas
        val barWidth = drawW / barCount
        val pointsPerBar = waveformData.size / barCount

        for (i in 0 until barCount) {
            val dataIdx = (i * pointsPerBar).coerceIn(0, waveformData.size - 1)
            val amplitude = waveformData[dataIdx]
            val barH = amplitude * drawH * 0.45f

            val x = padding + i * barWidth
            val barColor = if (x / w <= playbackProgress) progressColor else color

            drawRect(
                color = barColor.copy(alpha = 0.7f),
                topLeft = Offset(x, midY - barH),
                size = Size(barWidth * 0.8f, barH * 2)
            )
        }

        drawLine(
            color = color.copy(alpha = 0.3f),
            start = Offset(padding, midY),
            end = Offset(w - padding, midY),
            strokeWidth = 0.5f
        )

        if (playbackProgress > 0f) {
            val px = padding + playbackProgress * drawW
            drawLine(
                color = progressColor,
                start = Offset(px, padding),
                end = Offset(px, h - padding),
                strokeWidth = 2f
            )
        }
    }
}

@Composable
fun SignalPreviewBar(
    timings: List<Int>,
    modifier: Modifier = Modifier
) {
    val stats = remember(timings) {
        SignalProcessor.analyzeTimings(timings)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(SurfaceCard, RoundedCornerShape(6.dp))
            .border(1.dp, BorderSubtle, RoundedCornerShape(6.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        StatChip("Pulses", "${stats.totalPulses}")
        StatChip("Duration", "%.1f ms".format(stats.totalDurationMs))
        StatChip("Min", "${stats.minPulseUs} µs")
        StatChip("Max", "${stats.maxPulseUs} µs")
    }
}

@Composable
private fun StatChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.labelMedium, color = CyanAccent)
        Text(label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
}
