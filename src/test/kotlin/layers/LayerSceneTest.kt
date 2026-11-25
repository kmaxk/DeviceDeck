package layers

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LayerSceneTest {
    private fun node(index: Int, depth: Int, l: Int, t: Int, r: Int, b: Int) = ViewNode(
        index = index, parentIndex = if (index == 0) null else 0, depth = depth,
        className = "V$index", resourceId = "", text = "", contentDesc = "",
        bounds = Bounds(l, t, r, b),
    )

    private val root = node(0, 0, 0, 0, 100, 200)
    private val child = node(1, 1, 10, 10, 50, 50)
    private val empty = node(2, 2, 20, 20, 20, 40)
    private val nodes = listOf(root, child, empty)

    private fun view(yaw: Float = 0f, pitch: Float = 0f, gap: Float = 0f, scale: Float = 1f) =
        layerView(nodes, yaw, pitch, gap, scale, canvasCenter = Vec2(500f, 400f))

    private fun assertClose(expected: Float, actual: Float) =
        assertTrue(kotlin.math.abs(expected - actual) < 1e-3f, "expected $expected but was $actual")

    @Test
    fun paint_order_skips_empty_nodes_and_puts_deeper_nodes_last() {
        assertEquals(listOf(0, 1), paintOrder(nodes, 0f, 0f).map { it.index })
    }

    @Test
    fun paint_order_is_reversed_when_flipped() {
        assertEquals(listOf(1, 0), paintOrder(nodes, 180f, 0f).map { it.index })
    }

    @Test
    fun front_view_centers_content_on_canvas_and_applies_scale() {
        val v = view(scale = 2f)
        val center = v.planeFor(0).map(50f, 100f)
        assertClose(500f, center.x); assertClose(400f, center.y)
        val corner = v.planeFor(0).map(0f, 0f)
        assertClose(400f, corner.x); assertClose(200f, corner.y)
    }

    @Test
    fun pick_returns_front_most_node_under_point() {
        val v = view(scale = 2f)
        val p = v.planeFor(1).map(30f, 30f)
        assertEquals(1, pickNode(nodes, v, p))
    }

    @Test
    fun pick_falls_back_to_parent_outside_child() {
        val v = view(scale = 2f)
        assertEquals(0, pickNode(nodes, v, v.planeFor(0).map(80f, 150f)))
    }

    @Test
    fun pick_misses_outside_everything() {
        assertNull(pickNode(nodes, view(), Vec2(0f, 0f)))
    }

    @Test
    fun pick_follows_layer_offset_when_rotated() {
        // With a large gap the child's projection leaves the root's: the point must hit via the child's plane.
        val v = view(yaw = -30f, pitch = 15f, gap = 400f, scale = 1f)
        val onChild = v.planeFor(1).map(30f, 30f)
        assertEquals(1, pickNode(nodes, v, onChild))
        val back = assertNotNull(v.planeFor(1).unmap(onChild))
        assertClose(30f, back.x); assertClose(30f, back.y)
    }

    @Test
    fun pick_prefers_root_when_seen_from_behind() {
        // Seen from behind the root plane is in front, so a point covered by both hits the root.
        val v = view(yaw = 180f, gap = 0f)
        assertEquals(0, pickNode(nodes, v, v.planeFor(1).map(30f, 30f)))
    }

    @Test
    fun pick_ignores_edge_on_planes() {
        assertNull(pickNode(nodes, view(yaw = 90f), Vec2(500f, 400f)))
    }

    @Test
    fun empty_scene_has_no_pick() {
        val v = layerView(emptyList(), 0f, 0f, 10f, 1f, Vec2(0f, 0f))
        assertNull(pickNode(emptyList(), v, Vec2(0f, 0f)))
    }
}
