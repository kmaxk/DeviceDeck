package snapshot

import device.Platform
import ios.IosDevice
import ios.IosService
import ios.appearance
import ios.apps
import ios.deviceDetails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/** Reads apps and device details in parallel; a call that fails leaves its section out. */
suspend fun IosService.snapshot(device: IosDevice, label: String): Snapshot = withContext(Dispatchers.IO) {
    val sections = coroutineScope {
        val apps = async { apps(device).getOrNull() }
        val details = async { deviceDetails(device).getOrNull() }
        val appearance = async { appearance(device).getOrNull() }
        buildMap<String, Map<String, String>> {
            apps.await()?.let { put("apps", appsSection(it)) }
            details.await()?.let { put("device", deviceSection(it, appearance.await())) }
        }
    }
    val now = System.currentTimeMillis()
    Snapshot(
        id = SnapshotStore().newId(now), deviceId = device.id, deviceName = device.name,
        platform = Platform.IOS, takenAt = now, label = label, sections = sections,
    )
}
