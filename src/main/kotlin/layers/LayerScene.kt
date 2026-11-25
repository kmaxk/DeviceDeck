package layers

/**
 * Camera for the exploded view. Each depth gets its plane via [planeFor], which maps device
 * coordinates straight to canvas pixels; drawing and picking both use it, so they cannot disagree.
 *
 * The plane of depth d is `planeMap(yaw, pitch, d * gap)`, scaled by [scale] and shifted so the
 * scene's [pivot] (content center at [pivotDepth]) lands on [canvasCenter].
 */
class LayerView(
    val yaw: Float,
    val pitch: Float,
    val gap: Float,
    val scale: Float,
    val canvasCenter: Vec2,
    val pivot: Vec2,
    val pivotDepth: Float,
    /** Screen rect used to drop off-screen nodes (see [visibleNodes]); null = no screen filtering. */
    val screen: Bounds? = null,
) {
    private val pivotShift: Vec2 = planeMap(yaw, pitch, pivotDepth * gap).map(pivot.x, pivot.y)

    fun planeFor(depth: Int): PlaneMap {
        val m = planeMap(yaw, pitch, depth * gap)
        return PlaneMap(
            origin = Vec2(
                canvasCenter.x + scale * (m.origin.x - pivotShift.x),
                canvasCenter.y + scale * (m.origin.y - pivotShift.y),
            ),
            ex = Vec2(scale * m.ex.x, scale * m.ex.y),
            ey = Vec2(scale * m.ey.x, scale * m.ey.y),
        )
    }
}

/** Camera centered on the drawable nodes' bounding box and the middle of the depth range. */
fun layerView(
    nodes: List<ViewNode>,
    yaw: Float,
    pitch: Float,
    gap: Float,
    scale: Float,
    canvasCenter: Vec2,
    screen: Bounds? = null,
): LayerView {
    val drawable = visibleNodes(nodes, screen)
    val pivot = if (drawable.isEmpty()) Vec2(0f, 0f) else Vec2(
        (drawable.minOf { it.bounds.left } + drawable.maxOf { it.bounds.right }) / 2f,
        (drawable.minOf { it.bounds.top } + drawable.maxOf { it.bounds.bottom }) / 2f,
    )
    val maxDepth = drawable.maxOfOrNull { it.depth } ?: 0
    return LayerView(yaw, pitch, gap, scale, canvasCenter, pivot, maxDepth / 2f, screen)
}

/**
 * The device screen: the screenshot size when known, else the first top-level node's bounds,
 * else null (no screen filtering).
 */
fun screenRect(nodes: List<ViewNode>, imageWidth: Int?, imageHeight: Int?): Bounds? =
    if (imageWidth != null && imageHeight != null) Bounds(0, 0, imageWidth, imageHeight)
    else nodes.firstOrNull { it.depth == 0 && it.parentIndex == null }?.bounds

/** Nodes worth drawing: positive area and, when [screen] is given, overlapping it. */
fun visibleNodes(nodes: List<ViewNode>, screen: Bounds? = null): List<ViewNode> =
    nodes.filter { it.bounds.hasArea && (screen == null || it.bounds.intersects(screen)) }

/** Drawable nodes back to front: ascending depth (stable, so document order within a depth), reversed from behind. */
fun paintOrder(nodes: List<ViewNode>, yaw: Float, pitch: Float, screen: Bounds? = null): List<ViewNode> {
    val ordered = visibleNodes(nodes, screen).sortedBy { it.depth }
    return if (isFlipped(yaw, pitch)) ordered.asReversed() else ordered
}

/** Index of the front-most node whose projected rectangle contains [point] (canvas pixels), or null. */
fun pickNode(nodes: List<ViewNode>, view: LayerView, point: Vec2): Int? =
    paintOrder(nodes, view.yaw, view.pitch, view.screen).asReversed().firstOrNull { node ->
        val p = view.planeFor(node.depth).unmap(point) ?: return@firstOrNull false
        val b = node.bounds
        p.x >= b.left && p.x <= b.right && p.y >= b.top && p.y <= b.bottom
    }?.index
