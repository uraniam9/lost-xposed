package dev.lostxposed.core.diagnostics

enum class Status { INFO, PASS, WARN, FAIL }

data class Entry(
    val label: String,
    val status: Status,
    val detail: String,
    val hint: String? = null,
)

/**
 * A report is built per process. The hook side and the UI side never share memory, so each
 * assembles its own and they are reconciled for display.
 */
class DiagnosticsReport(val title: String) {

    private val entries = mutableListOf<Entry>()

    fun add(entry: Entry) = apply { entries += entry }

    fun info(label: String, detail: String) = add(Entry(label, Status.INFO, detail))
    fun pass(label: String, detail: String) = add(Entry(label, Status.PASS, detail))
    fun warn(label: String, detail: String, hint: String? = null) =
        add(Entry(label, Status.WARN, detail, hint))

    fun fail(label: String, detail: String, hint: String? = null) =
        add(Entry(label, Status.FAIL, detail, hint))

    fun entries(): List<Entry> = entries.toList()

    val worst: Status
        get() = entries.filter { it.status != Status.INFO }.maxByOrNull { it.status.ordinal }
            ?.status ?: Status.INFO

    fun render(): String = buildString {
        appendLine("=== $title ===")
        val width = entries.maxOfOrNull { it.label.length } ?: 0
        entries.forEach { e ->
            appendLine("[${e.status.name.padEnd(4)}] ${e.label.padEnd(width)}  ${e.detail}")
            e.hint?.let { appendLine("${" ".repeat(width + 9)}-> $it") }
        }
        appendLine("--- overall: $worst")
    }
}
