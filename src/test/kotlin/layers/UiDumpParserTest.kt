package layers

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UiDumpParserTest {
    private fun node(name: String, bounds: String = "[0,0][1080,1920]", text: String = "", children: String = "") =
        """<node index="0" text="$text" resource-id="id/$name" class="$name" content-desc="" bounds="$bounds">$children</node>"""

    private val dump = """<?xml version='1.0' encoding='UTF-8' standalone='yes' ?><hierarchy rotation="0">""" +
        node("A", children = node("B", children = node("C"))) + node("D") + "</hierarchy>"

    @Test
    fun parses_nesting_depth_and_parent() {
        val nodes = parseUiDump(dump)
        assertEquals(listOf("A", "B", "C", "D"), nodes.map { it.className })
        assertEquals(listOf(0, 1, 2, 0), nodes.map { it.depth })
        assertEquals(listOf(null, 0, 1, null), nodes.map { it.parentIndex })
        assertEquals(listOf(0, 1, 2, 3), nodes.map { it.index })
        assertEquals(Bounds(0, 0, 1080, 1920), nodes[0].bounds)
        assertEquals("id/A", nodes[0].resourceId)
    }

    @Test
    fun tolerates_text_around_xml() {
        val expected = parseUiDump(dump)
        assertEquals(expected, parseUiDump("$dump" + "UI hierchary dumped to: /dev/tty"))
        assertEquals(expected, parseUiDump("WARNING: linker something\n$dump" + "UI hierchary dumped to: /dev/tty"))
    }

    @Test
    fun keeps_special_characters_in_text() {
        val xml = "<hierarchy>" + node("A", text = "Tom &amp; Jerry 😀&#10;zwei") + "</hierarchy>"
        assertEquals("Tom & Jerry 😀\nzwei", parseUiDump(xml).single().text)
    }

    @Test
    fun skips_nodes_with_malformed_bounds() {
        val xml = "<hierarchy>" + node("A", bounds = "oops") + node("B") + "</hierarchy>"
        assertEquals(listOf("B"), parseUiDump(xml).map { it.className })
    }

    @Test
    fun returns_empty_for_blank_or_garbage_input() {
        assertTrue(parseUiDump("").isEmpty())
        assertTrue(parseUiDump("   \n").isEmpty())
        assertTrue(parseUiDump("ERROR: null root node returned by UiTestAutomationBridge.").isEmpty())
        assertTrue(parseUiDump("<hierarchy><node").isEmpty())
    }

    @Test
    fun bounds_intersects_ignores_touching_edges() {
        val a = Bounds(0, 0, 10, 10)
        assertFalse(a.intersects(Bounds(10, 0, 20, 10)))
        assertTrue(a.intersects(Bounds(9, 0, 20, 10)))
        assertTrue(a.hasArea)
        assertFalse(Bounds(5, 5, 5, 9).hasArea)
        assertNull(parseUiDump("").firstOrNull())
    }
}
