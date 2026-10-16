/** The aliases of the built-in styles that the `css` template variable accepts. */
internal object BuiltInStyles {
    const val NONE = "none"
    const val DEFAULT = "default"
    const val INTELLIJ = "intellij"
    const val GITHUB = "github"

    /** The CSS of each alias that stands for a stylesheet (`none` is none). */
    val css: Map<String, String> = mapOf(
        DEFAULT to INTELLIJ_CSS,
        INTELLIJ to INTELLIJ_CSS,
        GITHUB to GITHUB_CSS,
    )
}

/**
 * The `<head>` lines for the `css` template variable, in the order of its entries (so later ones can override
 * earlier ones): a built-in style (see [BuiltInStyles]) is a `<style>` element, `none` is nothing, and any other
 * entry is the URL of a stylesheet, which becomes `<link rel="stylesheet" href="...">` like in pandoc's template.
 *
 * Without any entry the `default` style is used; `none` is how to get no style at all.
 */
internal fun styleTags(context: TemplateContext): List<String> {
    return styleEntries(context).flatMap { entry ->
        when (entry) {
            BuiltInStyles.NONE -> emptyList()
            in BuiltInStyles.css -> {
                val css = BuiltInStyles.css.getValue(entry).trim('\n').lines()
                listOf("    <style>") + css.map { if (it.isEmpty()) it else "      $it" } + "    </style>"
            }
            else -> listOf("""    <link rel="stylesheet" href="${entry.escapeHtml()}">""")
        }
    }
}

/** The entries of the `css` variable, or the `default` style if there are none. */
internal fun styleEntries(context: TemplateContext): List<String> =
    (context["css"]?.let(::cssEntries) ?: emptyList()).ifEmpty { listOf(BuiltInStyles.DEFAULT) }

/** The non-blank entries of [value], a single value or a list, trimmed. */
private fun cssEntries(value: TemplateValue): List<String> =
    (if (value is TemplateValue.Items) value.values.flatMap(::cssEntries) else listOf(value.asText().trim()))
        .filter { it.isNotEmpty() }
