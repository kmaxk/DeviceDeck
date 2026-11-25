package layers

data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val hasArea: Boolean get() = width > 0 && height > 0

    /** True if the overlap has a positive area; merely touching edges does not count. */
    fun intersects(o: Bounds): Boolean =
        left < o.right && o.left < right && top < o.bottom && o.top < bottom
}

data class ViewNode(
    val index: Int,
    val parentIndex: Int?,
    val depth: Int,
    val className: String,
    val resourceId: String,
    val text: String,
    val contentDesc: String,
    val bounds: Bounds,
)
