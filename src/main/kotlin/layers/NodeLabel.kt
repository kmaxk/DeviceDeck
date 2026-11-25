package layers

/** Short label `Class#id`: simple class name, resource-id suffix after `:id/` (or the whole id), else the node index. */
fun nodeLabel(node: ViewNode): String {
    val cls = node.className.substringAfterLast('.').ifEmpty { "Node" }
    val id = node.resourceId.substringAfter(":id/").ifEmpty { node.index.toString() }
    return "$cls#$id"
}

/** Label for an overlapping pair: `Klasse#id A ↔ Klasse#id B`. */
fun overlapLabel(a: ViewNode, b: ViewNode): String = "${nodeLabel(a)} ↔ ${nodeLabel(b)}"
