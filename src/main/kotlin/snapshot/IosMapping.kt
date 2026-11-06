package snapshot

import ios.IosApp
import ios.IosDeviceDetails

/** Bundle id to version, shown like the Apps screen does ("1.2 (42)"). */
fun appsSection(apps: List<IosApp>): Map<String, String> = apps.associate { it.bundleId to it.displayVersion }

private val VolatileRowLabels = listOf("battery", "storage", "free", "size")

/** Device details as label to value, minus rows that change with usage; [appearance] is added when known. */
fun deviceSection(details: IosDeviceDetails, appearance: String?): Map<String, String> =
    buildMap {
        details.rows
            .filter { (label, _) -> VolatileRowLabels.none { label.contains(it, ignoreCase = true) } }
            .forEach { (label, value) -> put(label, value) }
        if (appearance != null) put("appearance", appearance)
    }
