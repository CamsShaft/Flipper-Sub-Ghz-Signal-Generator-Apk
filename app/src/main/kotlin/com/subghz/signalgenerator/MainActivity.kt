package com.subghz.signalgenerator

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.subghz.signalgenerator.core.AudioEngine
import com.subghz.signalgenerator.ui.screens.ConverterScreen
import com.subghz.signalgenerator.ui.screens.GeneratorScreen
import com.subghz.signalgenerator.ui.screens.InspectorScreen
import com.subghz.signalgenerator.ui.theme.*

class MainActivity : ComponentActivity() {

    private val audioEngine = AudioEngine()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestStoragePermission()
        enableEdgeToEdge()
        setContent {
            SubGhzTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = SurfaceBlack
                ) {
                    SubGhzApp(audioEngine)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        audioEngine.stop()
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubGhzApp(audioEngine: AudioEngine) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Generator", "Converter", "Inspector")

    var currentTimings by remember { mutableStateOf<List<Int>>(emptyList()) }

    Scaffold(
        containerColor = SurfaceBlack,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Sub-GHz Signal Generator",
                        style = MaterialTheme.typography.titleLarge,
                        color = FlipperOrange
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SurfaceBlack,
                    titleContentColor = FlipperOrange
                )
            )
        },
        bottomBar = {
            Column {
                HorizontalDivider(color = BorderSubtle, thickness = 1.dp)
                NavigationBar(
                    containerColor = SurfaceDark,
                    contentColor = TextPrimary,
                    tonalElevation = 0.dp
                ) {
                    tabs.forEachIndexed { index, title ->
                        NavigationBarItem(
                            icon = {},
                            label = {
                                Text(
                                    title,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (selectedTab == index) FlipperOrange else TextSecondary
                                )
                            },
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = FlipperOrange,
                                selectedTextColor = FlipperOrange,
                                unselectedIconColor = TextSecondary,
                                unselectedTextColor = TextSecondary,
                                indicatorColor = FlipperOrange.copy(alpha = 0.12f)
                            )
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(SurfaceBlack)
        ) {
            when (selectedTab) {
                0 -> GeneratorScreen(
                    onTimingsGenerated = { timings ->
                        currentTimings = timings
                    }
                )
                1 -> ConverterScreen(
                    onTimingsLoaded = { timings ->
                        currentTimings = timings
                    }
                )
                2 -> InspectorScreen(
                    timings = currentTimings,
                    audioEngine = audioEngine
                )
            }
        }
    }
}
