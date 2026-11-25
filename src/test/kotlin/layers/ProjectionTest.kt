package layers

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProjectionTest {
    private fun assertClose(expected: Float, actual: Float, msg: String = "") {
        assertTrue(kotlin.math.abs(expected - actual) < 1e-4f, "$msg expected $expected but was $actual")
    }

    @Test
    fun identity_at_zero_angles() {
        val m = planeMap(0f, 0f, 50f)
        assertClose(1f, m.ex.x); assertClose(0f, m.ex.y)
        assertClose(0f, m.ey.x); assertClose(1f, m.ey.y)
        assertClose(0f, m.origin.x); assertClose(0f, m.origin.y)
    }

    @Test
    fun yaw_90_collapses_plane_and_shifts_by_z() {
        val m = planeMap(90f, 0f, 50f)
        assertClose(0f, m.ex.x); assertClose(0f, m.ex.y)
        assertClose(50f, m.origin.x)
    }

    @Test
    fun pitch_shifts_layers_vertically() {
        val m = planeMap(0f, 30f, 50f)
        assertClose(-50f * 0.5f, m.origin.y)
    }

    @Test
    fun unmap_inverts_map() {
        val m = planeMap(25f, 15f, 40f)
        val p = m.map(120f, 340f)
        val back = assertNotNull(m.unmap(p))
        assertClose(120f, back.x)
        assertClose(340f, back.y)
    }

    @Test
    fun unmap_returns_null_when_edge_on() {
        assertNull(planeMap(90f, 0f, 10f).unmap(Vec2(1f, 1f)))
    }

    @Test
    fun flipped_beyond_90_degrees() {
        assertTrue(isFlipped(100f, 0f))
        assertFalse(isFlipped(80f, 0f))
    }
}
