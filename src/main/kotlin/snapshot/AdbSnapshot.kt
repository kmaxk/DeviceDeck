package snapshot

import adb.AdbDevice
import adb.AdbService
import device.Platform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Reads apps, settings, props and runtime permissions in parallel. A section whose output is
 * unusable (device offline, command failed) is left out instead of being stored empty.
 */
suspend fun AdbService.snapshot(device: AdbDevice, label: String): Snapshot = withContext(Dispatchers.IO) {
    val serial = device.serial
    val sections = coroutineScope {
        val apps = async { runCatching { parsePackageList(shell(serial, "pm list packages --show-versioncode")) }.getOrNull() }
        val settings = async {
            runCatching {
                listOf("system", "secure", "global").map { parseSettings(it, shell(serial, "settings list $it")) }
            }.getOrNull()
        }
        val props = async { runCatching { filterVolatileProps(parseProps(shell(serial, "getprop"))) }.getOrNull() }
        val permissions = async {
            runCatching {
                val dump = shell(serial, "dumpsys package")
                if ("Packages:" in dump) parsePermissions(dump) else null
            }.getOrNull()
        }
        buildMap<String, Map<String, String>> {
            apps.await()?.takeIf { it.isNotEmpty() }?.let { put("apps", it) }
            settings.await()?.takeIf { parts -> parts.any { it.isNotEmpty() } }?.let { put("settings", it.fold(emptyMap()) { acc, m -> acc + m }) }
            props.await()?.takeIf { it.isNotEmpty() }?.let { put("props", it) }
            permissions.await()?.let { put("permissions", it) }
        }
    }
    val now = System.currentTimeMillis()
    Snapshot(
        id = SnapshotStore().newId(now), deviceId = device.id, deviceName = device.name,
        platform = Platform.Android, takenAt = now, label = label, sections = sections,
    )
}
