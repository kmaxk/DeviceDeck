package layers

/**
 * Finds pairs of nodes whose bounds overlap with positive area, ignoring ancestor/descendant pairs.
 * Returns pairs of [ViewNode.index] with first < second, sorted.
 */
fun findOverlaps(nodes: List<ViewNode>): List<Pair<Int, Int>> {
    val byIndex = HashMap<Int, ViewNode>(nodes.size * 2)
    nodes.forEach { byIndex[it.index] = it }

    fun isAncestor(ancestor: ViewNode, node: ViewNode): Boolean {
        var p = node.parentIndex
        while (p != null) {
            if (p == ancestor.index) return true
            p = byIndex[p]?.parentIndex
        }
        return false
    }

    val candidates = nodes.filter { it.bounds.hasArea }.sortedBy { it.index }
    val result = ArrayList<Pair<Int, Int>>()
    for (i in candidates.indices) {
        val a = candidates[i]
        for (j in i + 1 until candidates.size) {
            val b = candidates[j]
            if (a.bounds.intersects(b.bounds) && !isAncestor(a, b) && !isAncestor(b, a)) {
                result += a.index to b.index
            }
        }
    }
    return result
}
