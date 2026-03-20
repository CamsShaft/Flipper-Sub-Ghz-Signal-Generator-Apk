package com.subghz.signalgenerator.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.os.Environment
import com.subghz.signalgenerator.core.*
import com.subghz.signalgenerator.ui.components.*
import com.subghz.signalgenerator.ui.theme.*
import java.io.File

private val SUB_FILES_DIR = File(Environment.getExternalStorageDirectory(), "sub-files")

@Composable
fun GeneratorScreen(
    onTimingsGenerated: (List<Int>) -> Unit
) {
    val context = LocalContext.current

    // Signal parameters
    var selectedFrequency by remember { mutableStateOf(SubGhzFrequency.DEFAULT) }
    var customFrequencyHz by remember { mutableStateOf("433920000") }
    var selectedPreset by remember { mutableStateOf(SubGhzPreset.DEFAULT) }
    var selectedProtocol by remember { mutableStateOf(SubGhzKnownProtocol.PRINCETON) }
    var repeatCountText by remember { mutableStateOf("5") }
    var gapUsText by remember { mutableStateOf("10000") }

    // Input mode
    var inputMode by remember { mutableIntStateOf(0) }
    val inputModes = listOf("Protocol", "Binary", "Hex", "Raw", "DTMF")

    // Protocol-specific
    var protocolCode by remember { mutableStateOf("") }
    var pulseUsText by remember { mutableStateOf("350") }

    // Raw/Binary/Hex input
    var rawInput by remember { mutableStateOf("") }
    var selectedEncoding by remember { mutableStateOf(SignalEncoding.PWM) }
    var shortUsText by remember { mutableStateOf("350") }
    var longUsText by remember { mutableStateOf("1050") }

    // Byte-to-timing parameters
    var minUsText by remember { mutableStateOf("100") }
    var maxUsText by remember { mutableStateOf("2000") }

    // DTMF parameters
    var dtmfSequence by remember { mutableStateOf("") }
    var dtmfToneMsText by remember { mutableStateOf("100") }
    var dtmfPauseMsText by remember { mutableStateOf("100") }
    var dtmfSampleRate by remember { mutableIntStateOf(8000) }

    // Generated signal
    var currentTimings by remember { mutableStateOf<List<Int>>(emptyList()) }
    var fileName by remember { mutableStateOf("signal") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // -- RF Configuration --
        SectionHeader("RF Configuration")
        SubGhzCard {
            SubGhzDropdown(
                label = "Frequency",
                items = SubGhzFrequency.entries.toList(),
                selectedItem = selectedFrequency,
                onItemSelected = { selectedFrequency = it },
                itemLabel = { it.label }
            )

            if (selectedFrequency == SubGhzFrequency.CUSTOM) {
                SubGhzTextField(
                    value = customFrequencyHz,
                    onValueChange = { customFrequencyHz = it },
                    label = "Custom Frequency (Hz)",
                    keyboardType = KeyboardType.Number
                )
            }

            SubGhzDropdown(
                label = "Preset / Modulation",
                items = SubGhzPreset.entries.toList(),
                selectedItem = selectedPreset,
                onItemSelected = { selectedPreset = it },
                itemLabel = { it.label }
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SubGhzTextField(
                    value = repeatCountText,
                    onValueChange = { repeatCountText = it.filter { c -> c.isDigit() } },
                    label = "Repeats",
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f)
                )
                SubGhzTextField(
                    value = gapUsText,
                    onValueChange = { gapUsText = it.filter { c -> c.isDigit() } },
                    label = "Gap (µs)",
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // -- Input Mode --
        SectionHeader("Signal Input")
        SegmentedSelector(
            options = inputModes,
            selectedIndex = inputMode,
            onSelected = { inputMode = it }
        )

        SubGhzCard {
            when (inputMode) {
                0 -> { // Protocol
                    SubGhzDropdown(
                        label = "Protocol",
                        items = SubGhzKnownProtocol.entries.filter { it != SubGhzKnownProtocol.RAW },
                        selectedItem = selectedProtocol,
                        onItemSelected = { selectedProtocol = it },
                        itemLabel = { it.label }
                    )

                    SubGhzTextField(
                        value = protocolCode,
                        onValueChange = { protocolCode = it },
                        label = "Code (decimal or 0x hex)",
                        keyboardType = KeyboardType.Text
                    )

                    SubGhzTextField(
                        value = pulseUsText,
                        onValueChange = { pulseUsText = it.filter { c -> c.isDigit() } },
                        label = "Base Pulse Width (µs)",
                        keyboardType = KeyboardType.Number
                    )

                    InfoRow("Bit Length", "${selectedProtocol.bitLength} bits")
                    InfoRow("Encoding", selectedProtocol.encoding.label)
                }
                1 -> { // Binary
                    SubGhzTextField(
                        value = rawInput,
                        onValueChange = { rawInput = it.filter { c -> c == '0' || c == '1' || c == ' ' } },
                        label = "Binary Data (0s and 1s)",
                        singleLine = false,
                        maxLines = 5
                    )

                    SubGhzDropdown(
                        label = "Encoding",
                        items = SignalEncoding.entries.toList(),
                        selectedItem = selectedEncoding,
                        onItemSelected = { selectedEncoding = it },
                        itemLabel = { it.label }
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SubGhzTextField(
                            value = shortUsText,
                            onValueChange = { shortUsText = it.filter { c -> c.isDigit() } },
                            label = "Short (µs)",
                            keyboardType = KeyboardType.Number,
                            modifier = Modifier.weight(1f)
                        )
                        SubGhzTextField(
                            value = longUsText,
                            onValueChange = { longUsText = it.filter { c -> c.isDigit() } },
                            label = "Long (µs)",
                            keyboardType = KeyboardType.Number,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                2 -> { // Hex
                    SubGhzTextField(
                        value = rawInput,
                        onValueChange = { rawInput = it },
                        label = "Hex Data (AA BB CC or AABBCC)",
                        singleLine = false,
                        maxLines = 5
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SubGhzTextField(
                            value = minUsText,
                            onValueChange = { minUsText = it.filter { c -> c.isDigit() } },
                            label = "Min µs",
                            keyboardType = KeyboardType.Number,
                            modifier = Modifier.weight(1f)
                        )
                        SubGhzTextField(
                            value = maxUsText,
                            onValueChange = { maxUsText = it.filter { c -> c.isDigit() } },
                            label = "Max µs",
                            keyboardType = KeyboardType.Number,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                3 -> { // Raw timings
                    SubGhzTextField(
                        value = rawInput,
                        onValueChange = { rawInput = it },
                        label = "Raw Timings (space or comma separated)",
                        singleLine = false,
                        maxLines = 8
                    )

                    Text(
                        "Enter positive values for HIGH pulses, negative for LOW.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                4 -> { // DTMF
                    DtmfInputPanel(
                        sequence = dtmfSequence,
                        onSequenceChange = { dtmfSequence = it },
                        toneMsText = dtmfToneMsText,
                        onToneMsTextChange = { dtmfToneMsText = it },
                        pauseMsText = dtmfPauseMsText,
                        onPauseMsTextChange = { dtmfPauseMsText = it },
                        sampleRate = dtmfSampleRate,
                        onSampleRateChange = { dtmfSampleRate = it }
                    )
                }
            }
        }

        // -- Generate --
        SubGhzButton(
            text = "Generate Signal",
            onClick = {
                val pulseUs = pulseUsText.toIntOrNull()?.coerceIn(50, 5000) ?: 350
                val shortUs = shortUsText.toIntOrNull()?.coerceIn(50, 10000) ?: 350
                val longUs = longUsText.toIntOrNull()?.coerceIn(50, 50000) ?: 1050
                val minUs = minUsText.toIntOrNull()?.coerceIn(10, 10000) ?: 100
                val maxUs = maxUsText.toIntOrNull()?.coerceIn(10, 50000) ?: 2000
                val dtmfToneMs = dtmfToneMsText.toIntOrNull()?.coerceIn(20, 500) ?: 100
                val dtmfPauseMs = dtmfPauseMsText.toIntOrNull()?.coerceIn(20, 500) ?: 100
                currentTimings = generateTimings(
                    inputMode, selectedProtocol, protocolCode, pulseUs,
                    rawInput, selectedEncoding, shortUs, longUs, minUs, maxUs,
                    dtmfSequence, dtmfToneMs, dtmfPauseMs, dtmfSampleRate
                )
                onTimingsGenerated(currentTimings)
            },
            modifier = Modifier.fillMaxWidth()
        )

        // -- Preview --
        if (currentTimings.isNotEmpty()) {
            SectionHeader("Signal Preview")
            SignalPreviewBar(currentTimings)
            DigitalWaveform(
                timings = currentTimings,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
            )

            // -- Export --
            SectionHeader("Export")
            SubGhzCard {
                SubGhzTextField(
                    value = fileName,
                    onValueChange = { fileName = it.replace(Regex("[^a-zA-Z0-9_\\-]"), "") },
                    label = "File Name"
                )

                val freq = if (selectedFrequency == SubGhzFrequency.CUSTOM)
                    customFrequencyHz.toLongOrNull() ?: SubGhzFrequency.DEFAULT.hz
                else selectedFrequency.hz

                SubGhzButton(
                    text = "Export .sub File",
                    onClick = {
                        val repeatCount = repeatCountText.toIntOrNull()?.coerceIn(1, 100) ?: 5
                        val gapUs = gapUsText.toIntOrNull()?.coerceIn(100, 100000) ?: 10000
                        val signal = SubGhzSignal(
                            frequency = freq,
                            preset = selectedPreset,
                            timings = currentTimings,
                            repeatCount = repeatCount,
                            gapUs = gapUs
                        )
                        exportSubFile(context, fileName, signal)
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

// ---- DTMF Input Panel with visual keypad ----

@Composable
private fun DtmfInputPanel(
    sequence: String,
    onSequenceChange: (String) -> Unit,
    toneMsText: String,
    onToneMsTextChange: (String) -> Unit,
    pauseMsText: String,
    onPauseMsTextChange: (String) -> Unit,
    sampleRate: Int,
    onSampleRateChange: (Int) -> Unit
) {
    // Sequence display
    SubGhzTextField(
        value = sequence,
        onValueChange = { input ->
            onSequenceChange(input.uppercase().filter { it in DtmfEncoder.VALID_CHARS })
        },
        label = "DTMF Sequence"
    )

    // Visual keypad
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        DtmfEncoder.KEYPAD_ROWS.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                row.forEach { key ->
                    DtmfKey(
                        key = key,
                        onClick = {
                            if (sequence.length < 32) { // cap sequence length
                                onSequenceChange(sequence + key)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Clear / Backspace row
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(RedSignal.copy(alpha = 0.2f))
                    .border(1.dp, RedSignal.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .clickable { onSequenceChange("") },
                contentAlignment = Alignment.Center
            ) {
                Text("CLEAR", color = RedSignal, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(AmberWarn.copy(alpha = 0.15f))
                    .border(1.dp, AmberWarn.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .clickable {
                        if (sequence.isNotEmpty()) onSequenceChange(sequence.dropLast(1))
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("DEL", color = AmberWarn, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    // Tone info for current sequence
    if (sequence.isNotEmpty()) {
        val lastChar = sequence.last()
        val freqs = DtmfEncoder.getFrequencies(lastChar)
        if (freqs != null) {
            InfoRow("Last Digit", "'$lastChar'")
            InfoRow("Low Tone", "${freqs.first.toInt()} Hz")
            InfoRow("High Tone", "${freqs.second.toInt()} Hz")
        }
        InfoRow("Sequence Length", "${sequence.length} digits")

        val toneMs = toneMsText.toIntOrNull() ?: 100
        val pauseMs = pauseMsText.toIntOrNull() ?: 100
        val totalMs = sequence.length * toneMs + (sequence.length - 1) * pauseMs
        InfoRow("Est. Duration", "${totalMs} ms")
    }

    HorizontalDivider(color = BorderSubtle)

    // Timing controls
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SubGhzTextField(
            value = toneMsText,
            onValueChange = { onToneMsTextChange(it.filter { c -> c.isDigit() }) },
            label = "Tone (ms)",
            keyboardType = KeyboardType.Number,
            modifier = Modifier.weight(1f)
        )
        SubGhzTextField(
            value = pauseMsText,
            onValueChange = { onPauseMsTextChange(it.filter { c -> c.isDigit() }) },
            label = "Pause (ms)",
            keyboardType = KeyboardType.Number,
            modifier = Modifier.weight(1f)
        )
    }

    SubGhzDropdown(
        label = "Sample Rate",
        items = listOf(4000, 8000, 16000, 22050),
        selectedItem = sampleRate,
        onItemSelected = { onSampleRateChange(it) },
        itemLabel = { "$it Hz" }
    )

    Text(
        "Higher sample rate = more precise OOK pulse timing but more data. " +
        "8000 Hz is a good balance.",
        style = MaterialTheme.typography.bodySmall,
        color = TextSecondary
    )
}

@Composable
private fun DtmfKey(
    key: Char,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLetter = key in 'A'..'D'
    val bgColor = if (isLetter) CyanAccent.copy(alpha = 0.12f)
                  else FlipperOrange.copy(alpha = 0.12f)
    val borderColor = if (isLetter) CyanAccent.copy(alpha = 0.3f)
                      else FlipperOrange.copy(alpha = 0.3f)
    val textColor = if (isLetter) CyanAccent else FlipperOrange

    Box(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = key.toString(),
            color = textColor,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

// ---- Signal Generation ----

private fun generateTimings(
    inputMode: Int,
    protocol: SubGhzKnownProtocol,
    codeStr: String,
    pulseUs: Int,
    rawInput: String,
    encoding: SignalEncoding,
    shortUs: Int,
    longUs: Int,
    minUs: Int,
    maxUs: Int,
    dtmfSequence: String,
    dtmfToneMs: Int,
    dtmfPauseMs: Int,
    dtmfSampleRate: Int
): List<Int> {
    return when (inputMode) {
        0 -> { // Protocol
            val code = if (codeStr.startsWith("0x", ignoreCase = true))
                codeStr.removePrefix("0x").removePrefix("0X").toLongOrNull(16) ?: 0L
            else codeStr.toLongOrNull() ?: 0L

            when (protocol) {
                SubGhzKnownProtocol.PRINCETON, SubGhzKnownProtocol.GATE_TX, SubGhzKnownProtocol.HOLTEK ->
                    ProtocolEncoder.encodePrinceton(code, protocol.bitLength, pulseUs)
                SubGhzKnownProtocol.CAME ->
                    ProtocolEncoder.encodeCame(code, protocol.bitLength, pulseUs)
                SubGhzKnownProtocol.NICE_FLO ->
                    ProtocolEncoder.encodeNiceFlo(code, protocol.bitLength, pulseUs)
                SubGhzKnownProtocol.LINEAR ->
                    ProtocolEncoder.encodeLinear(code, protocol.bitLength, pulseUs)
                else ->
                    ProtocolEncoder.encodePrinceton(code, protocol.bitLength, pulseUs)
            }
        }
        1 -> SignalProcessor.binaryStringToTimings(rawInput, shortUs, longUs, encoding)
        2 -> SignalProcessor.hexToTimings(rawInput, minUs, maxUs)
        3 -> SignalProcessor.parseRawTimings(rawInput)
        4 -> DtmfEncoder.encode(dtmfSequence, dtmfToneMs, dtmfPauseMs, dtmfSampleRate)
        else -> emptyList()
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
