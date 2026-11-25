package layers

import kotlin.test.Test
import kotlin.test.assertEquals

class NodeLabelTest {
    private fun node(index: Int, cls: String, id: String = "") =
        ViewNode(index, null, 0, cls, id, "", "", Bounds(0, 0, 1, 1))

    @Test
    fun uses_simple_class_name_and_id_suffix() {
        assertEquals("Button#submit", nodeLabel(node(3, "android.widget.Button", "com.app:id/submit")))
    }

    @Test
    fun falls_back_to_index_without_resource_id() {
        assertEquals("TextView#7", nodeLabel(node(7, "android.widget.TextView")))
    }

    @Test
    fun resource_id_without_id_marker_is_used_whole() {
        assertEquals("View#foo", nodeLabel(node(1, "View", "foo")))
    }

    @Test
    fun empty_class_name_becomes_node() {
        assertEquals("Node#2", nodeLabel(node(2, "")))
    }

    @Test
    fun pair_label_joins_with_arrow() {
        val a = node(0, "a.A", "p:id/x")
        val b = node(5, "a.B")
        assertEquals("A#x ↔ B#5", overlapLabel(a, b))
    }
}
