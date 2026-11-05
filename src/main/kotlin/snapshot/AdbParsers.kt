package snapshot

/** `pm list packages --show-versioncode` → package to versionCode. */
fun parsePackageList(output: String): Map<String, String> =
    output.lineSequence()
        .mapNotNull { Regex("""^package:(\S+)(?:\s+versionCode:(\S*))?""").find(it.trim()) }
        .associate { it.groupValues[1] to it.groupValues[2] }

/** `settings list <namespace>` → `<namespace>/<key>` to value; split at the first `=` only. */
fun parseSettings(namespace: String, output: String): Map<String, String> =
    output.lineSequence()
        .mapNotNull { line ->
            val i = line.indexOf('=')
            if (i <= 0) null else "$namespace/${line.substring(0, i)}" to line.substring(i + 1)
        }
        .toMap()

/** Runtime permissions of every package in a full `dumpsys package` dump → `<package>/<permission>` to granted/denied. */
fun parsePermissions(dump: String): Map<String, String> {
    val result = LinkedHashMap<String, String>()
    val packageHeader = Regex("""^\s*Package \[(.+?)]""")
    val sharedHeader = Regex("""^\s*(?:SharedUser|Shared users:|Hidden system packages:)""")
    val permission = Regex("""^\s+([\w.]+): granted=(true|false)""")
    var pkg: String? = null
    var inRuntime = false
    for (line in dump.lineSequence()) {
        val header = packageHeader.find(line)
        if (header != null) { pkg = header.groupValues[1]; inRuntime = false; continue }
        if (sharedHeader.containsMatchIn(line)) { pkg = null; inRuntime = false; continue }
        if (line.trim() == "runtime permissions:") { inRuntime = pkg != null; continue }
        if (inRuntime) {
            val m = permission.find(line)
            if (m != null) result["$pkg/${m.groupValues[1]}"] = if (m.groupValues[2] == "true") "granted" else "denied"
            else inRuntime = false
        }
    }
    return result
}

/** `getprop` output, values may span several lines: `[key]: [line one\nline two]`. */
fun parseProps(output: String): Map<String, String> {
    val result = LinkedHashMap<String, String>()
    val start = Regex("""^\[([^\]]+)]: \[(.*)$""")
    var key: String? = null
    val value = StringBuilder()
    for (line in output.lineSequence()) {
        if (key == null) {
            val m = start.find(line) ?: continue
            key = m.groupValues[1]
            value.setLength(0)
            value.append(m.groupValues[2])
        } else {
            value.append('\n').append(line)
        }
        if (value.endsWith("]")) {
            result[key!!] = value.substring(0, value.length - 1)
            key = null
        }
    }
    return result
}

private val VolatilePropPatterns = listOf(
    Regex("""^sys\.boot_completed$"""), Regex("""^ro\.runtime\.firstboot$"""), Regex("""^sys\.uptime.*"""),
    Regex("""^debug\..*"""), Regex("""^persist\.sys\.boot\.reason.*"""), Regex("""^init\.svc\..*"""),
    Regex("""^sys\.sysctl\..*"""), Regex("""^ro\.boottime\..*"""),
)

/** Drops props that change on every boot or poll, so they don't drown real changes in a diff. */
fun filterVolatileProps(props: Map<String, String>): Map<String, String> =
    props.filterKeys { key -> VolatilePropPatterns.none { it.matches(key) } }
