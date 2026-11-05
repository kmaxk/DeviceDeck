package snapshot

import device.Platform
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SnapshotStoreTest {
    private val root: File = Files.createTempDirectory("snapshots-test").toFile()
    private val store = SnapshotStore(root)

    @AfterTest
    fun cleanup() {
        root.deleteRecursively()
    }

    private fun snap(id: String, deviceId: String = "dev1", sections: Map<String, Map<String, String>> = mapOf("apps" to mapOf("a" to "1"))) =
        Snapshot(id, deviceId, "Device", Platform.Android, 0L, "label", sections)

    @Test
    fun save_then_list_roundtrips() {
        val saved = store.save(snap("20261105-120000"))
        assertEquals(listOf(saved), store.list("dev1"))
    }

    @Test
    fun list_is_newest_first() {
        store.save(snap("20261105-120000"))
        store.save(snap("20261105-130000"))
        store.save(snap("20261105-110000"))
        assertEquals(listOf("20261105-130000", "20261105-120000", "20261105-110000"), store.list("dev1").map { it.id })
    }

    @Test
    fun same_second_does_not_overwrite() {
        val first = store.save(snap("20261105-120000"))
        val second = store.save(snap("20261105-120000", sections = mapOf("apps" to mapOf("b" to "2"))))
        assertEquals("20261105-120000", first.id)
        assertEquals("20261105-120000-2", second.id)
        assertEquals(2, store.list("dev1").size)
    }

    @Test
    fun device_id_with_colon_is_sanitized() {
        val id = "192.168.1.5:5555"
        store.save(snap("20261105-120000", deviceId = id))
        assertTrue(root.listFiles()!!.none { ':' in it.name })
        assertEquals(1, store.list(id).size)
    }

    @Test
    fun corrupt_file_is_skipped() {
        store.save(snap("20261105-120000"))
        File(root, store.dirNameFor("dev1")).resolve("20261105-130000.json").writeText("{ not json")
        assertEquals(listOf("20261105-120000"), store.list("dev1").map { it.id })
    }

    @Test
    fun unknown_json_fields_are_ignored() {
        val saved = store.save(snap("20261105-120000"))
        val file = File(root, store.dirNameFor("dev1")).resolve("${saved.id}.json")
        file.writeText(file.readText().replaceFirst("{", "{\"futureField\":42,"))
        assertEquals(1, store.list("dev1").size)
    }

    @Test
    fun delete_removes_file() {
        val saved = store.save(snap("20261105-120000"))
        store.delete(saved)
        assertTrue(store.list("dev1").isEmpty())
    }

    @Test
    fun list_for_unknown_device_is_empty() {
        assertTrue(store.list("nope").isEmpty())
    }

    @Test
    fun new_id_has_timestamp_format() {
        assertTrue(Regex("""\d{8}-\d{6}""").matches(store.newId()))
        assertFalse(store.newId(0L).isEmpty())
    }
}
