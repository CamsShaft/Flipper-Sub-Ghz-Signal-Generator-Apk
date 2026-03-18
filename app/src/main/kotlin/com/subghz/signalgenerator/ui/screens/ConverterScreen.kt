package com.subghz.signalgenerator.ui.screens

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.subghz.signalgenerator.core.*
import com.subghz.signalgenerator.ui.components.*
import com.subghz.signalgenerator.ui.theme.*
import java.io.File

private val SUB_FILES_DIR = File(Environment.getExternalStorageDirectory(), "sub-files")

@Composable
fun ConverterScreen(
    onTimingsLoaded: (List<Int>) -> Unit
) {
    val context = LocalContext.current

    var sourceType by remember { mutableIntStateOf(0) }
    val sourceTypes = listOf("Binary", ".sub File", "Text/CSV")

    var loadedFileName by remember { mutableStateOf("") }
    var loadedData by remember { mutableStateOf<ByteArray?>(null) }
    var loadedText by remember { mutableStateOf("") }
    var parsedTimings by remember { mutableStateOf<List<Int>>(emptyList()) }
    var parsedSignal by remember { mutableStateOf<SubGhzSignal?>(null) }
    var truncationWarning by remember { mutableStateOf<String?>(null) }

    var selectedFrequency by remember { mutableStateOf(SubGhzFrequency.DEFAULT) }
    var selectedPreset by remember { mutableStateOf(SubGhzPreset.DEFAULT) }
    var minUs by remember { mutableIntStateOf(100) }
    var maxUs by remember { mutableIntStateOf(2000) }
    var repeatCount by remember { mutableIntStateOf(5) }
    var gapUs by remember { mutableIntStateOf(10000) }
    var selectedEncoding by remember { mutableStateOf(SignalEncoding.PWM) }
    var shortUs by remember { mutableIntStateOf(350) }
    var longUs by remember { mutableIntStateOf(1050) }

    var outputName by remember { mutableStateOf("converted") }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                val rawBytes = inputStream?.readBytes()
                inputStream?.close()

                if (rawBytes == null) {
                    Toast.makeText(context, "Could not read file", Toast.LENGTH_SHORT).show()
                    return@let
                }

                loadedFileName = it.lastPathSegment ?: "unknown"

                // Check file size and truncate if needed
                val sizeWarning = SignalProcessor.checkLimits(byteCount = rawBytes.size)
                val bytes = if (rawBytes.size > SignalProcessor.MAX_INPUT_BYTES)
                    rawBytes.copyOf(SignalProcessor.MAX_INPUT_BYTES)
                else rawBytes
                truncationWarning = sizeWarning

                when (sourceType) {
                    0 -> {
                        loadedData = bytes
                        loadedText = ""
                        parsedTimings = emptyList()
                    }
                    1 -> {
                        loadedText = bytes.decodeToString()
                        loadedData = null
                        val signal = SubFileGenerator.parseSubFile(loadedText)
                        if (signal != null) {
                            parsedSignal = signal
                            // Cap loaded timings
                            parsedTimings = if (signal.timings.size > SignalProcessor.MAX_TIMINGS) {
                                truncationWarning = SignalProcessor.checkLimits(
                                    timingCount = signal.timings.size
                                )
                                signal.timings.take(SignalProcessor.MAX_TIMINGS)
                            } else signal.timings
                            selectedFrequency = SubGhzFrequency.entries.find { f ->
                                f.hz == signal.frequency
                            } ?: SubGhzFrequency.CUSTOM
                            selectedPreset = signal.preset
                            onTimingsLoaded(parsedTimings)
                        } else {
                            Toast.makeText(context, "Invalid .sub file format", Toast.LENGTH_SHORT).show()
                        }
                    }
                    2 -> {
                        loadedText = bytes.decodeToString()
                        loadedData = null
                        parsedTimings = emptyList()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error loading file: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionHeader("File Converter")

        SegmentedSelector(
            options = sourceTypes,
            selectedIndex = sourceType,
            onSelected = {
                sourceType = it
                loadedData = null
                loadedText = ""
                parsedTimings = emptyList()
                parsedSignal = null
                loadedFileName = ""
                truncationWarning = null
            }
        )

        SubGhzCard {
            SubGhzButton(
                text = if (loadedFileName.isEmpty()) "Load File" else "File: $loadedFileName",
                onClick = {
                    val mimeType = when (sourceType) {
                        2 -> "text/*"
                        else -> "*/*"
                    }
                    filePicker.launch(mimeType)
                },
                modifier = Modifier.fillMaxWidth(),
                isPrimary = loadedFileName.isEmpty()
            )

            // Truncation warning
            truncationWarning?.let { warning ->
                Text(warning, style = MaterialTheme.typography.bodySmall, color = AmberWarn)
            }

            if (loadedData != null) {
                InfoRow("File Size", formatSize(loadedData!!.size))
                InfoRow("Type", "Binary data")
                val hexPreview = loadedData!!.take(32)
                    .joinToString(" ") { "%02X".format(it) }
                Text(
                    "Preview: $hexPreview${if (loadedData!!.size > 32) "..." else ""}",
                    style = MaterialTheme.typography.labelMedium,
                    color = CyanAccent
                )
            }

            if (loadedText.isNotEmpty() && sourceType == 2) {
                InfoRow("Lines", "${loadedText.lines().size}")
                InfoRow("Characters", "${loadedText.length}")
            }

            if (parsedSignal != null && sourceType == 1) {
                val stats = SignalProcessor.analyzeTimings(parsedTimings)
                InfoRow("Frequency", "${parsedSignal!!.frequency} Hz")
                InfoRow("Preset", parsedSignal!!.preset.label)
                InfoRow("Timings", "${stats.totalPulses} pulses")
                InfoRow("Duration", "%.2f ms".format(stats.totalDurationMs))
            }
        }

        if (sourceType != 1 || parsedTimings.isEmpty()) {
            SectionHeader("Conversion Settings")
            SubGhzCard {
                SubGhzDropdown(
                    label = "Frequency",
                    items = SubGhzFrequency.entries.toList(),
                    selectedItem = selectedFrequency,
                    onItemSelected = { selectedFrequency = it },
                    itemLabel = { it.label }
                )

                SubGhzDropdown(
                    label = "Preset",
                    items = SubGhzPreset.entries.toList(),
                    selectedItem = selectedPreset,
                    onItemSelected = { selectedPreset = it },
                    itemLabel = { it.label }
                )

                when (sourceType) {
                    0 -> {
                        SubGhzDropdown(
                            label = "Encoding",
                            items = SignalEncoding.entries.toList(),
                            selectedItem = selectedEncoding,
                            onItemSelected = { selectedEncoding = it },
                            itemLabel = { it.label }
                        )

                        if (selectedEncoding == SignalEncoding.RAW) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                SubGhzTextField(
                                    value = minUs.toString(),
                                    onValueChange = { minUs = it.toIntOrNull()?.coerceIn(10, 10000) ?: 100 },
                                    label = "Min µs",
                                    keyboardType = KeyboardType.Number,
                                    modifier = Modifier.weight(1f)
                                )
                                SubGhzTextField(
                                    value = maxUs.toString(),
                                    onValueChange = { maxUs = it.toIntOrNull()?.coerceIn(10, 50000) ?: 2000 },
                                    label = "Max µs",
                                    keyboardType = KeyboardType.Number,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                SubGhzTextField(
                                    value = shortUs.toString(),
                                    onValueChange = { shortUs = it.toIntOrNull()?.coerceIn(50, 10000) ?: 350 },
                                    label = "Short (µs)",
                                    keyboardType = KeyboardType.Number,
                                    modifier = Modifier.weight(1f)
                                )
                                SubGhzTextField(
                                    value = longUs.toString(),
                                    onValueChange = { longUs = it.toIntOrNull()?.coerceIn(50, 50000) ?: 1050 },
                                    label = "Long (µs)",
                                    keyboardType = KeyboardType.Number,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                    2 -> {
                        Text(
                            "Supports comma or space separated timing values, or binary strings (0s and 1s).",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SubGhzTextField(
                        value = repeatCount.toString(),
                        onValueChange = { repeatCount = it.toIntOrNull()?.coerceIn(1, 100) ?: 5 },
                        label = "Repeats",
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(1f)
                    )
                    SubGhzTextField(
                        value = gapUs.toString(),
                        onValueChange = { gapUs = it.toIntOrNull()?.coerceIn(100, 100000) ?: 10000 },
                        label = "Gap (µs)",
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            SubGhzButton(
                text = "Convert to Signal",
                onClick = {
                    parsedTimings = when (sourceType) {
                        0 -> {
                            val bytes = loadedData ?: return@SubGhzButton
                            when (selectedEncoding) {
                                SignalEncoding.RAW ->
                                    SignalProcessor.bytesToTimings(bytes, minUs, maxUs)
                                SignalEncoding.PWM ->
                                    ProtocolEncoder.encodePwm(bytes, shortUs, longUs)
                                SignalEncoding.MANCHESTER ->
                                    ProtocolEncoder.encodeManchester(bytes, shortUs * 2)
                                else ->
                                    SignalProcessor.bytesToTimings(bytes, minUs, maxUs)
                            }
                        }
                        2 -> {
                            if (loadedText.isBlank()) return@SubGhzButton
                            val cleaned = loadedText.trim()
                            if (cleaned.all { it == '0' || it == '1' || it.isWhitespace() }) {
                                SignalProcessor.binaryStringToTimings(cleaned, shortUs, longUs)
                            } else {
                                SignalProcessor.parseRawTimings(cleaned)
                            }
                        }
                        else -> emptyList()
                    }
                    truncationWarning = SignalProcessor.checkLimits(timingCount = parsedTimings.size)
                    onTimingsLoaded(parsedTimings)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = loadedData != null || loadedText.isNotEmpty()
            )
        }

        if (parsedTimings.isNotEmpty()) {
            SectionHeader("Converted Signal")
            SignalPreviewBar(parsedTimings)
            DigitalWaveform(
                timings = parsedTimings,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
            )

            SectionHeader("Export")
            SubGhzCard {
                SubGhzTextField(
                    value = outputName,
                    onValueChange = { outputName = it.replace(Regex("[^a-zA-Z0-9_\\-]"), "") },
                    label = "Output File Name"
                )

                val freq = selectedFrequency.hz.let {
                    if (it == 0L) SubGhzFrequency.DEFAULT.hz else it
                }

                SubGhzButton(
                    text = "Export .sub File",
                    onClick = {
                        val signal = SubGhzSignal(
                            frequency = freq,
                            preset = selectedPreset,
                            timings = parsedTimings,
                            repeatCount = repeatCount,
                            gapUs = gapUs
                        )
                        exportSubFile(context, outputName, signal)
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    "Saves to: ${SUB_FILES_DIR.absolutePath}/",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

private fun exportSubFile(context: Context, name: String, signal: SubGhzSignal) {
    try {
        val content = SubFileGenerator.generate(signal)
        SUB_FILES_DIR.mkdirs()
        val file = File(SUB_FILES_DIR, "${name}.sub")
        file.writeText(content)
        Toast.makeText(context, "Saved: ${file.absolutePath}", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

private fun formatSize(bytes: Int): String {
    return when {
        bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
        bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
        else -> "$bytes bytes"
    }
}
