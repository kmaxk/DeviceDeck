package ui

import androidx.compose.ui.geometry.Offset
import layers.Vec2
import layers.planeMap
import kotlin.test.Test
import kotlin.test.assertTrue

/** The texture is drawn through [toMatrix]; picking uses PlaneMap.unmap. Both must agree. */
class LayersMatrixTest {
    @Test
    fun matrix_maps_points_like_plane_map() {
        val plane = planeMap(-30f, 15f, 120f).let {
            layers.PlaneMap(Vec2(it.origin.x + 300f, it.origin.y + 200f), it.ex, it.ey)
        }
        val m = plane.toMatrix()
        for ((x, y) in listOf(0f to 0f, 1080f to 0f, 0f to 2400f, 345f to 678f)) {
            val expected = plane.map(x, y)
            val actual = m.map(Offset(x, y))
            assertTrue(kotlin.math.abs(expected.x - actual.x) < 1e-2f, "x at ($x,$y): $expected vs $actual")
            assertTrue(kotlin.math.abs(expected.y - actual.y) < 1e-2f, "y at ($x,$y): $expected vs $actual")
        }
    }
}
