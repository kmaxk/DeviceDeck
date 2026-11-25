package layers

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

data class Vec2(val x: Float, val y: Float)

/** Affine map from layer-plane coordinates to screen: origin + x*ex + y*ey. */
class PlaneMap(val origin: Vec2, val ex: Vec2, val ey: Vec2) {
    fun map(x: Float, y: Float): Vec2 =
        Vec2(origin.x + x * ex.x + y * ey.x, origin.y + x * ex.y + y * ey.y)

    /** Inverse of [map]; null when the plane is (nearly) edge-on. */
    fun unmap(p: Vec2): Vec2? {
        val det = ex.x * ey.y - ey.x * ex.y
        if (abs(det) < 1e-4f) return null
        val dx = p.x - origin.x
        val dy = p.y - origin.y
        return Vec2((dx * ey.y - ey.x * dy) / det, (ex.x * dy - ex.y * dx) / det)
    }
}

private fun rad(deg: Float): Float = Math.toRadians(deg.toDouble()).toFloat()

/** Orthographic projection: rotate around Y (yaw), then X (pitch), drop Z. */
fun planeMap(yawDeg: Float, pitchDeg: Float, z: Float): PlaneMap {
    val cy = cos(rad(yawDeg)); val sy = sin(rad(yawDeg))
    val cp = cos(rad(pitchDeg)); val sp = sin(rad(pitchDeg))
    return PlaneMap(
        origin = Vec2(z * sy, -z * cy * sp),
        ex = Vec2(cy, sy * sp),
        ey = Vec2(0f, cp),
    )
}

/** True when the view is from behind; planes must then be drawn in reverse order. */
fun isFlipped(yawDeg: Float, pitchDeg: Float): Boolean =
    cos(rad(yawDeg)) * cos(rad(pitchDeg)) < 0f
