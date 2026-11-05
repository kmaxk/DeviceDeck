package snapshot

import device.Platform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SnapshotDiffTest {
    private fun snap(vararg sections: Pair<String, Map<String, String>>) = Snapshot(
        id = "20261105-120000", deviceId = "d", deviceName = "Device", platform = Platform.Android,
        takenAt = 0L, label = "", sections = mapOf(*sections),
    )

    private fun compared(result: SectionResult?): SectionDiff {
        assertIs<SectionResult.Compared>(result)
        return result.diff
    }

    @Test
    fun added_removed_changed_are_detected() {
        val before = snap("apps" to mapOf("a" to "1", "b" to "1"))
        val after = snap("apps" to mapOf("b" to "2", "c" to "1"))
        val d = compared(diff(before, after)["apps"])
        assertEquals(mapOf("c" to "1"), d.added)
        assertEquals(mapOf("a" to "1"), d.removed)
        assertEquals(mapOf("b" to ("1" to "2")), d.changed)
    }

    @Test
    fun identical_snapshots_yield_empty_diffs() {
        val s = snap("apps" to mapOf("a" to "1"), "props" to mapOf("x" to "y"))
        val result = diff(s, s)
        assertEquals(setOf("apps", "props"), result.keys)
        assertTrue(result.values.all { compared(it).isEmpty })
    }

    @Test
    fun section_missing_in_one_snapshot_is_not_comparable() {
        val before = snap("permissions" to mapOf("p/x" to "granted"))
        val after = snap()
        assertEquals(SectionResult.NotComparable, diff(before, after)["permissions"])
        assertEquals(SectionResult.NotComparable, diff(after, before)["permissions"])
    }

    @Test
    fun section_missing_in_both_is_absent_from_result() {
        assertFalse(diff(snap(), snap()).containsKey("apps"))
    }

    @Test
    fun empty_section_vs_filled_section_is_compared() {
        val d = compared(diff(snap("apps" to emptyMap()), snap("apps" to mapOf("a" to "1")))["apps"])
        assertEquals(mapOf("a" to "1"), d.added)
        assertTrue(d.removed.isEmpty() && d.changed.isEmpty())
    }
}
