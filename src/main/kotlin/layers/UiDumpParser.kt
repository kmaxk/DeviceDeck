package layers

import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

private val BOUNDS_REGEX = Regex("""\[(-?\d+),(-?\d+)]\[(-?\d+),(-?\d+)]""")

/**
 * Parses a uiautomator dump into a flat list in document order. Tolerates noise around the XML
 * (e.g. "UI hierchary dumped to: ..."). Nodes with malformed bounds are skipped; parse errors yield an empty list.
 */
fun parseUiDump(xml: String): List<ViewNode> {
    val start = xml.indexOf("<hierarchy")
    val endTag = "</hierarchy>"
    val end = xml.lastIndexOf(endTag)
    if (start < 0 || end < start) return emptyList()
    val clipped = xml.substring(start, end + endTag.length)
    return try {
        val factory = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            isXIncludeAware = false
            isExpandEntityReferences = false
        }
        val root = factory.newDocumentBuilder().parse(InputSource(StringReader(clipped))).documentElement
        val out = mutableListOf<ViewNode>()
        collect(root, null, 0, out)
        out
    } catch (e: Exception) {
        emptyList()
    }
}

private fun collect(parent: Element, parentIndex: Int?, depth: Int, out: MutableList<ViewNode>) {
    val children = parent.childNodes
    for (i in 0 until children.length) {
        val el = children.item(i) as? Element ?: continue
        if (el.tagName != "node") continue
        val m = BOUNDS_REGEX.matchEntire(el.getAttribute("bounds").trim())
        if (m == null) continue
        val (l, t, r, b) = m.destructured
        val index = out.size
        out += ViewNode(
            index = index,
            parentIndex = parentIndex,
            depth = depth,
            className = el.getAttribute("class"),
            resourceId = el.getAttribute("resource-id"),
            text = el.getAttribute("text"),
            contentDesc = el.getAttribute("content-desc"),
            bounds = Bounds(l.toInt(), t.toInt(), r.toInt(), b.toInt()),
        )
        collect(el, index, depth + 1, out)
    }
}
