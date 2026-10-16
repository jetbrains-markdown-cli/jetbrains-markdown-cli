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

/** A template variable has a value its use does not allow, e.g. `highlight=ture`. */
class InvalidVariableException(message: String) : IllegalArgumentException(message)

/**
 * The value of the variable [name] as a boolean: `true` and `false` (also as text, in any case), and the empty
 * text is `false` like in pandoc. A list is its last value.
 *
 * @throws InvalidVariableException for anything else, which is more likely a typo than a deliberate `false`
 */
internal fun TemplateValue.asBoolean(name: String): Boolean = when (this) {
    is TemplateValue.Flag -> value
    is TemplateValue.Items -> values.lastOrNull()?.asBoolean(name) ?: false
    is TemplateValue.Text -> when (value.trim().lowercase()) {
        "true" -> true
        "false", "" -> false
        else -> throw InvalidVariableException("the variable `$name` must be true or false, not `$value`")
    }
    is TemplateValue.Fields -> throw InvalidVariableException("the variable `$name` must be true or false, not a map")
}
