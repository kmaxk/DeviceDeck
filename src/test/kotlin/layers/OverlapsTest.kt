package layers

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OverlapsTest {
    private fun node(index: Int, parent: Int?, bounds: Bounds, depth: Int = 0) =
        ViewNode(index, parent, depth, "N$index", "", "", "", bounds)

    private fun b(l: Int, t: Int, r: Int, bt: Int) = Bounds(l, t, r, bt)

    @Test
    fun siblings_that_intersect_overlap() {
        val nodes = listOf(node(0, null, b(0, 0, 100, 100)), node(1, null, b(50, 50, 150, 150)))
        assertEquals(listOf(0 to 1), findOverlaps(nodes))
    }

    @Test
    fun parent_and_child_never_overlap() {
        val nodes = listOf(node(0, null, b(0, 0, 100, 100)), node(1, 0, b(10, 10, 50, 50)))
        assertEquals(emptyList(), findOverlaps(nodes))
    }

    @Test
    fun grandparent_and_grandchild_never_overlap() {
        val nodes = listOf(
            node(0, null, b(0, 0, 100, 100)),
            node(1, 0, b(0, 0, 100, 100)),
            node(2, 1, b(10, 10, 50, 50)),
        )
        assertEquals(emptyList(), findOverlaps(nodes))
    }

    @Test
    fun touching_siblings_do_not_overlap() {
        val nodes = listOf(node(0, null, b(0, 0, 100, 100)), node(1, null, b(100, 0, 200, 100)))
        assertEquals(emptyList(), findOverlaps(nodes))
    }

    @Test
    fun zero_area_nodes_are_ignored() {
        val nodes = listOf(node(0, null, b(0, 0, 100, 100)), node(1, null, b(50, 50, 50, 150)))
        assertEquals(emptyList(), findOverlaps(nodes))
    }

    @Test
    fun identical_bounds_siblings_overlap() {
        val nodes = listOf(node(0, null, b(0, 0, 10, 10)), node(1, null, b(0, 0, 10, 10)))
        assertEquals(listOf(0 to 1), findOverlaps(nodes))
    }

    @Test
    fun handles_800_nodes_quickly() {
        val nodes = (0 until 800).map { i ->
            val x = (i % 20) * 50
            val y = (i / 20) * 50
            node(i, null, b(x, y, x + 51, y + 51))
        }
        val result = findOverlaps(nodes)
        assertTrue(result.isNotEmpty())
        assertTrue(result.all { it.first < it.second })
        assertEquals(result.sortedWith(compareBy({ it.first }, { it.second })), result)
    }

    private val screen = b(0, 0, 100, 100)

    @Test
    fun offscreen_sibling_produces_no_pair() {
        val nodes = listOf(
            node(0, null, b(0, 0, 100, 100)),
            node(1, 0, b(10, 10, 50, 50), depth = 1),
            node(2, 0, b(150, 0, 250, 100), depth = 1),
            node(3, 0, b(200, 50, 300, 150), depth = 1),
        )
        assertEquals(listOf(2 to 3), findOverlaps(nodes))
        assertEquals(emptyList(), findOverlaps(nodes, screen))
    }

    @Test
    fun filtered_out_middle_ancestor_keeps_grandparent_and_grandchild_apart() {
        val zeroAreaMiddle = listOf(
            node(0, null, b(0, 0, 100, 100)),
            node(1, 0, b(0, 0, 0, 0), depth = 1),
            node(2, 1, b(10, 10, 50, 50), depth = 2),
        )
        assertEquals(emptyList(), findOverlaps(zeroAreaMiddle, screen))
        val offscreenMiddle = listOf(
            node(0, null, b(0, 0, 100, 100)),
            node(1, 0, b(-300, 0, -200, 100), depth = 1),
            node(2, 1, b(10, 10, 50, 50), depth = 2),
        )
        assertEquals(emptyList(), findOverlaps(offscreenMiddle, screen))
    }

    @Test
    fun null_screen_behaves_as_before() {
        val nodes = listOf(
            node(0, null, b(0, 0, 100, 100)),
            node(1, null, b(150, 0, 250, 100)),
            node(2, null, b(200, 50, 300, 150)),
        )
        assertEquals(findOverlaps(nodes), findOverlaps(nodes, null))
        assertEquals(listOf(1 to 2), findOverlaps(nodes, null))
    }
}
