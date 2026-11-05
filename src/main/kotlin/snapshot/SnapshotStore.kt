package snapshot

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

/** Snapshots as JSON files: `<root>/<device>/<id>.json`. Prefs can't hold them (8 KB limit per value). */
class SnapshotStore(
    private val root: File = File(System.getProperty("user.home"), ".devicedeck/snapshots"),
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun newId(now: Long = System.currentTimeMillis()): String =
        SimpleDateFormat("yyyyMMdd-HHmmss").format(Date(now))

    /** Adb serials like `192.168.1.5:5555` contain characters that are invalid in file names on Windows. */
    fun dirNameFor(deviceId: String): String = deviceId.replace(Regex("[^A-Za-z0-9._-]"), "_")

    private fun dir(deviceId: String) = File(root, dirNameFor(deviceId))

    /** Stores [snapshot]; a taken id gets `-2`, `-3`, … appended. Returns the stored snapshot. */
    @Synchronized
    fun save(snapshot: Snapshot): Snapshot {
        val dir = dir(snapshot.deviceId).apply { mkdirs() }
        var id = snapshot.id
        var n = 2
        while (File(dir, "$id.json").exists()) id = "${snapshot.id}-${n++}"
        val stored = snapshot.copy(id = id)
        File(dir, "$id.json").writeText(json.encodeToString(stored))
        return stored
    }

    /** Newest first; unreadable files are skipped. */
    fun list(deviceId: String): List<Snapshot> =
        dir(deviceId).listFiles { f -> f.extension == "json" }.orEmpty()
            .mapNotNull { runCatching { json.decodeFromString<Snapshot>(it.readText()) }.getOrNull() }
            .sortedByDescending { it.id }

    fun delete(snapshot: Snapshot) {
        File(dir(snapshot.deviceId), "${snapshot.id}.json").delete()
    }
}
