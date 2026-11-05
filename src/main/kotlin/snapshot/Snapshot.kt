package snapshot

import device.Platform
import kotlinx.serialization.Serializable

/**
 * A device's state at one point in time. [sections] maps a section name (apps, settings, …) to
 * flat key/value pairs; a section the collector couldn't read is simply absent.
 */
@Serializable
data class Snapshot(
    val id: String,
    val deviceId: String,
    val deviceName: String,
    val platform: Platform,
    val takenAt: Long,
    val label: String,
    val sections: Map<String, Map<String, String>>,
)
