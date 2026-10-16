/** A template value, modelled on pandoc's: text, boolean, list or map. */
sealed interface TemplateValue {
    data class Text(val value: String) : TemplateValue
    data class Flag(val value: Boolean) : TemplateValue
    data class Items(val values: List<TemplateValue>) : TemplateValue
    data class Fields(val values: Map<String, TemplateValue>) : TemplateValue
}

/**
 * The text of the value as pandoc inserts it into a template: lists are concatenated,
 * `true` is `true` and `false` and maps are empty.
 */
internal fun TemplateValue.asText(): String = when (this) {
    is TemplateValue.Text -> value
    is TemplateValue.Flag -> if (value) "true" else ""
    is TemplateValue.Items -> values.joinToString("") { it.asText() }
    is TemplateValue.Fields -> ""
}

/** Template variables by name. */
typealias TemplateContext = Map<String, TemplateValue>

/**
 * Parses a `-V`/`--variable` argument like pandoc: `KEY=VAL` or `KEY:VAL` (whichever separator comes first),
 * or just `KEY`, which is the boolean `true`.
 */
internal fun parseVariable(argument: String): Pair<String, TemplateValue> {
    val separator = argument.indexOfAny(charArrayOf('=', ':'))
    val key = if (separator < 0) argument else argument.substring(0, separator)
    require(key.isNotEmpty()) { "the variable name must not be empty" }
    return key to if (separator < 0) TemplateValue.Flag(true) else TemplateValue.Text(argument.substring(separator + 1))
}

/** Collects variable assignments; a variable assigned more than once becomes a list, like in pandoc. */
internal fun variablesOf(assignments: List<Pair<String, TemplateValue>>): TemplateContext {
    val context = LinkedHashMap<String, TemplateValue>()
    for ((key, value) in assignments) {
        context[key] = when (val previous = context[key]) {
            null -> value
            is TemplateValue.Items -> TemplateValue.Items(previous.values + value)
            else -> TemplateValue.Items(listOf(previous, value))
        }
    }
    return context
}
