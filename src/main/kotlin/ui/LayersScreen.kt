package ui

import adb.AdbDevice
import adb.AdbService
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Rotate90DegreesCcw
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import layers.Bounds
import layers.PlaneMap
import layers.Vec2
import layers.ViewNode
import layers.findOverlaps
import layers.layerView
import layers.overlapLabel
import layers.paintOrder
import layers.screenRect
import layers.visibleNodes
import layers.pickNode
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

private const val DEFAULT_YAW = -30f
private const val DEFAULT_PITCH = 15f
private const val DEFAULT_GAP = 40f
private const val MIN_ZOOM = 0.25f
private const val MAX_ZOOM = 8f
private const val DEGREES_PER_PX = 0.4f

/** Exploded 3D view of the uiautomator hierarchy: side panel (capture, view controls, details) next to the canvas. */
@Composable
fun LayersScreen(device: AdbDevice) {
    val scope = rememberCoroutineScope()
    var nodes by remember(device.serial) { mutableStateOf<List<ViewNode>>(emptyList()) }
    var screenshot by remember(device.serial) { mutableStateOf<ImageBitmap?>(null) }
    var loading by remember { mutableStateOf(false) }
    var feedback by remember(device.serial) { mutableStateOf<String?>(null) }
    var yaw by remember { mutableStateOf(DEFAULT_YAW) }
    var pitch by remember { mutableStateOf(DEFAULT_PITCH) }
    var zoom by remember { mutableStateOf(1f) }
    var layerGap by remember { mutableStateOf(DEFAULT_GAP) }
    var selected by remember(device.serial) { mutableStateOf<Int?>(null) }
    val screen = remember(nodes, screenshot) { screenRect(nodes, screenshot?.width, screenshot?.height) }
    val overlaps = remember(nodes, screen) { findOverlaps(nodes, screen) }
    val overlapping = remember(overlaps) { overlaps.flatMap { listOf(it.first, it.second) }.toSet() }

    Row(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .width(340.dp)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard("Layers", Icons.Filled.Layers) {
                Button(
                    enabled = !loading,
                    onClick = {
                        loading = true
                        feedback = null
                        scope.launch {
                            try {
                                val (dump, shot) = coroutineScope {
                                    val d = async { AdbService.uiDump(device.serial) }
                                    val s = async { AdbService.screenshot(device.serial) }
                                    d.await() to s.await()
                                }
                                // Hierarchy and screenshot are one unit: on dump failure keep the old pair untouched.
                                feedback = dump.fold(
                                    onSuccess = { dumped ->
                                        val image = shot.getOrNull()?.let { decodeImage(it) }
                                        nodes = dumped
                                        selected = null
                                        screenshot = image
                                        "${dumped.size} views captured" +
                                            if (image == null) " (screenshot failed – untextured)" else ""
                                    },
                                    onFailure = { it.message ?: "Capture failed" },
                                )
                            } finally {
                                loading = false
                            }
                        }
                    },
                ) { Text(if (loading) "Capturing…" else "Capture") }
                feedback?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }

            SectionCard("View", Icons.Filled.Rotate90DegreesCcw) {
                Text(
                    "Drag to rotate, scroll to zoom, click a view to inspect it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("Layer gap: ${layerGap.toInt()} px", style = MaterialTheme.typography.bodySmall)
                Slider(value = layerGap, onValueChange = { layerGap = it }, valueRange = 0f..120f)
                OutlinedButton(onClick = {
                    yaw = DEFAULT_YAW
                    pitch = DEFAULT_PITCH
                    zoom = 1f
                    layerGap = DEFAULT_GAP
                }) { Text("Reset view") }
            }

            NodeDetails(selected?.let { nodes.getOrNull(it) })

            OverlapList(nodes, overlaps, onSelect = { selected = it })
        }

        Box(Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outline))

        Box(
            modifier = Modifier.weight(1f).fillMaxHeight().background(Bench.Well),
            contentAlignment = Alignment.Center,
        ) {
            if (nodes.isEmpty()) {
                EmptyState(Icons.Filled.Layers, "No hierarchy yet", "Capture the device screen to explore its layers")
            } else {
                LayerCanvas(
                    nodes = nodes,
                    image = screenshot,
                    screen = screen,
                    yaw = yaw,
                    pitch = pitch,
                    zoom = zoom,
                    layerGap = layerGap,
                    selected = selected,
                    overlapping = overlapping,
                    onRotate = { d ->
                        yaw = wrapDegrees(yaw + d.x * DEGREES_PER_PX)
                        pitch = (pitch - d.y * DEGREES_PER_PX).coerceIn(-89f, 89f)
                    },
                    onZoom = { scrollY -> zoom = (zoom * 1.1f.pow(-scrollY)).coerceIn(MIN_ZOOM, MAX_ZOOM) },
                    onSelect = { selected = it },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/** Keeps yaw in -180..180 so it does not grow without bound while dragging. */
private fun wrapDegrees(deg: Float): Float = ((deg + 180f) % 360f + 360f) % 360f - 180f

/** Canvas drawing every node with area as a textured quad on its depth plane; handles rotate, zoom and pick. */
@Composable
private fun LayerCanvas(
    nodes: List<ViewNode>,
    image: ImageBitmap?,
    screen: Bounds?,
    yaw: Float,
    pitch: Float,
    zoom: Float,
    layerGap: Float,
    selected: Int?,
    overlapping: Set<Int>,
    onRotate: (Offset) -> Unit,
    onZoom: (Float) -> Unit,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val view = remember(nodes, screen, yaw, pitch, zoom, layerGap, canvasSize) {
        layerView(
            nodes, yaw, pitch, layerGap,
            scale = fitScale(visibleNodes(nodes, screen), canvasSize) * zoom,
            canvasCenter = Vec2(canvasSize.width / 2f, canvasSize.height / 2f),
            screen = screen,
        )
    }
    val order = remember(nodes, screen, yaw, pitch) { paintOrder(nodes, yaw, pitch, screen) }
    val currentView by rememberUpdatedState(view)
    val currentNodes by rememberUpdatedState(nodes)
    val rotate by rememberUpdatedState(onRotate)
    val zoomBy by rememberUpdatedState(onZoom)
    val select by rememberUpdatedState(onSelect)

    val outline = Bench.Slate.copy(alpha = 0.6f)
    val highlight = MaterialTheme.colorScheme.primary
    val overlapColor = MaterialTheme.colorScheme.error
    val untextured = Bench.Raised.copy(alpha = 0.5f)

    Canvas(
        modifier
            .clipToBounds()
            .onSizeChanged { canvasSize = it }
            .pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    change.consume()
                    rotate(drag)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { p -> select(pickNode(currentNodes, currentView, Vec2(p.x, p.y))) }
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Scroll) {
                            val dy = event.changes.sumOf { it.scrollDelta.y.toDouble() }.toFloat()
                            if (dy != 0f) zoomBy(dy)
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
            }
    ) {
        // Pass 1: textures and plain outlines, back to front.
        for (node in order) {
            val plane = view.planeFor(node.depth)
            val shape = quad(plane, node)
            if (image == null || !drawTexture(image, node, plane)) drawPath(shape, untextured)
            drawPath(shape, outline, style = Stroke(width = 1f))
        }
        // Pass 2: highlights on top of everything, so deeper opaque textures cannot hide them.
        // Overlaps first (red), the selection last (primary, thicker) so it stays distinguishable.
        for (node in order) {
            if (node.index !in overlapping || node.index == selected) continue
            val shape = quad(view.planeFor(node.depth), node)
            drawPath(shape, overlapColor.copy(alpha = 0.12f))
            drawPath(shape, overlapColor, style = Stroke(width = 1.5f))
        }
        order.firstOrNull { it.index == selected }?.let { node ->
            val shape = quad(view.planeFor(node.depth), node)
            drawPath(shape, highlight.copy(alpha = 0.25f))
            drawPath(shape, highlight, style = Stroke(width = 2.5f))
        }
    }
}

/** Scale at zoom 1: the box around the already-filtered [drawable] nodes fills 80 % of the shorter canvas fit. */
private fun fitScale(drawable: List<ViewNode>, canvas: IntSize): Float {
    if (drawable.isEmpty() || canvas.width == 0 || canvas.height == 0) return 1f
    val w = max(1, drawable.maxOf { it.bounds.right } - drawable.minOf { it.bounds.left })
    val h = max(1, drawable.maxOf { it.bounds.bottom } - drawable.minOf { it.bounds.top })
    return 0.8f * min(canvas.width / w.toFloat(), canvas.height / h.toFloat())
}

/**
 * Draws the screenshot pixels under [node]'s bounds onto its plane. Source and destination
 * rect are the same device rect; the plane matrix maps it to the canvas. False if nothing to draw.
 */
private fun DrawScope.drawTexture(image: ImageBitmap, node: ViewNode, plane: PlaneMap): Boolean {
    val b = node.bounds
    val left = b.left.coerceIn(0, image.width)
    val top = b.top.coerceIn(0, image.height)
    val right = b.right.coerceIn(0, image.width)
    val bottom = b.bottom.coerceIn(0, image.height)
    if (right <= left || bottom <= top) return false
    val offset = IntOffset(left, top)
    val size = IntSize(right - left, bottom - top)
    withTransform({ transform(plane.toMatrix()) }) {
        drawImage(image, srcOffset = offset, srcSize = size, dstOffset = offset, dstSize = size)
    }
    return true
}

/** The node's rectangle projected onto the canvas, built in canvas space so strokes stay hairline. */
private fun quad(plane: PlaneMap, node: ViewNode): Path {
    val b = node.bounds
    val corners = listOf(
        plane.map(b.left.toFloat(), b.top.toFloat()),
        plane.map(b.right.toFloat(), b.top.toFloat()),
        plane.map(b.right.toFloat(), b.bottom.toFloat()),
        plane.map(b.left.toFloat(), b.bottom.toFloat()),
    )
    return Path().apply {
        moveTo(corners[0].x, corners[0].y)
        corners.drop(1).forEach { lineTo(it.x, it.y) }
        close()
    }
}

/** Affine 2D matrix equal to [PlaneMap.map]: canvas = origin + x*ex + y*ey. */
internal fun PlaneMap.toMatrix(): Matrix = Matrix().apply {
    this[0, 0] = ex.x; this[0, 1] = ex.y
    this[1, 0] = ey.x; this[1, 1] = ey.y
    this[3, 0] = origin.x; this[3, 1] = origin.y
}

@Composable
private fun NodeDetails(node: ViewNode?) {
    SectionCard("Details", Icons.Filled.Info) {
        if (node == null) {
            Text(
                "Click a view in the canvas.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SectionCard
        }
        val b = node.bounds
        SelectionContainer {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                DetailRow("Class", node.className)
                DetailRow("Resource ID", node.resourceId)
                DetailRow("Text", node.text)
                DetailRow("Content desc", node.contentDesc)
                DetailRow("Bounds", "[${b.left},${b.top}][${b.right},${b.bottom}]  ${b.width}×${b.height}")
                DetailRow("Depth", node.depth.toString())
            }
        }
    }
}

/** Side card listing overlapping pairs; clicking an entry selects the pair's first node. */
@Composable
private fun OverlapList(nodes: List<ViewNode>, overlaps: List<Pair<Int, Int>>, onSelect: (Int) -> Unit) {
    val byIndex = remember(nodes) { nodes.associateBy { it.index } }
    SectionCard("Overlaps (${overlaps.size})", Icons.Filled.Warning) {
        Text(
            "Overlaps are geometric only (the dump has no z-order or transparency), so stacked views can be false alarms.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (overlaps.isEmpty()) {
            Text("No overlaps found.", style = MaterialTheme.typography.bodySmall)
        } else {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 240.dp)) {
                items(overlaps) { (a, b) ->
                    val na = byIndex[a]
                    val nb = byIndex[b]
                    if (na != null && nb != null) {
                        Text(
                            overlapLabel(na, nb),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.fillMaxWidth().clickable { onSelect(a) }.padding(vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp),
        )
        Text(value.ifEmpty { "—" }, style = MaterialTheme.typography.bodySmall)
    }
}
