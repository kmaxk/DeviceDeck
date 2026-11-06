package snapshot

data class SectionDiff(
    val added: Map<String, String>,
    val removed: Map<String, String>,
    val changed: Map<String, Pair<String, String>>,
) {
    val isEmpty get() = added.isEmpty() && removed.isEmpty() && changed.isEmpty()
}

sealed interface SectionResult {
    data class Compared(val diff: SectionDiff) : SectionResult
    /** The section exists in only one of the two snapshots, so a comparison would be misleading. */
    data object NotComparable : SectionResult
}

/** Compares every section present in either snapshot; sections absent from both don't appear. */
fun diff(before: Snapshot, after: Snapshot): Map<String, SectionResult> =
    (before.sections.keys + after.sections.keys).associateWith { name ->
        val a = before.sections[name]
        val b = after.sections[name]
        if (a == null || b == null) {
            SectionResult.NotComparable
        } else {
            SectionResult.Compared(
                SectionDiff(
                    added = b.filterKeys { it !in a },
                    removed = a.filterKeys { it !in b },
                    changed = a.mapNotNull { (k, old) -> b[k]?.takeIf { it != old }?.let { k to (old to it) } }.toMap(),
                )
            )
        }
    }
