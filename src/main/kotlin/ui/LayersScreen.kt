package ui

import adb.AdbDevice
import adb.AdbService
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import layers.ViewNode

/** Placeholder: captures the view hierarchy and a screenshot; the 3D view follows. */
@Composable
fun LayersScreen(device: AdbDevice) {
    val scope = rememberCoroutineScope()
    var nodes by remember(device.serial) { mutableStateOf<List<ViewNode>>(emptyList()) }
    var screenshot by remember(device.serial) { mutableStateOf<ImageBitmap?>(null) }
    var loading by remember { mutableStateOf(false) }
    var feedback by remember(device.serial) { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionCard("Layers", Icons.Filled.Layers) {
            Button(
                enabled = !loading,
                onClick = {
                    loading = true
                    feedback = null
                    scope.launch {
                        val (dump, shot) = coroutineScope {
                            val d = async { AdbService.uiDump(device.serial) }
                            val s = async { AdbService.screenshot(device.serial) }
                            d.await() to s.await()
                        }
                        dump.onSuccess { nodes = it }
                        screenshot = shot.getOrNull()?.let { decodeImage(it) } ?: screenshot
                        feedback = dump.fold(
                            onSuccess = { "${it.size} views captured" },
                            onFailure = { it.message ?: "Capture failed" },
                        )
                        loading = false
                    }
                },
            ) { Text(if (loading) "Capturing…" else "Capture") }
            feedback?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}
