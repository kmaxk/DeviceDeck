package snapshot

import ios.IosApp
import ios.IosDeviceDetails
import kotlin.test.Test
import kotlin.test.assertEquals

class IosMappingTest {
    private fun app(id: String, version: String, build: String) =
        IosApp(bundleId = id, name = id, version = version, build = build, path = "", isSystem = false, isRemovable = true)

    @Test
    fun apps_section_maps_bundle_id_to_display_version() {
        val apps = listOf(app("com.a", "1.2", "42"), app("com.b", "", "7"), app("com.c", "3.0", "3.0"))
        assertEquals(mapOf("com.a" to "1.2 (42)", "com.b" to "7", "com.c" to "3.0"), appsSection(apps))
    }

    @Test
    fun device_section_maps_rows() {
        val details = IosDeviceDetails(listOf("Name" to "iPhone 15", "iOS" to "17.5 (21F79)"))
        assertEquals(mapOf("Name" to "iPhone 15", "iOS" to "17.5 (21F79)"), deviceSection(details, null))
    }

    @Test
    fun device_section_adds_appearance_only_when_present() {
        val details = IosDeviceDetails(listOf("Name" to "Sim"))
        assertEquals(mapOf("Name" to "Sim", "appearance" to "dark"), deviceSection(details, "dark"))
        assertEquals(mapOf("Name" to "Sim"), deviceSection(details, null))
    }

    @Test
    fun volatile_device_rows_are_dropped() {
        val details = IosDeviceDetails(
            listOf("Name" to "Sim", "Battery" to "80%", "Storage" to "128 GB", "Free space" to "10 GB", "Data size" to "1 GB")
        )
        assertEquals(mapOf("Name" to "Sim"), deviceSection(details, null))
    }
}
