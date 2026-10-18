/** The aliases of the built-in styles that the `css` template variable accepts. */
internal object BuiltInStyles {
    const val NONE = "none"
    const val DEFAULT = "default"
    const val INTELLIJ = "intellij"
    const val GITHUB = "github"
    const val GITLAB = "gitlab"
    const val PANDOC = "pandoc"

    /**
     * The base rules (print layout, images) that every `css` list gets in front unless it has `none` or `common`
     * itself. Not one of the page styles in [css]: it has no look of its own, so it does not count as "the built-in
     * style of the page" for the colours of highlighting and diagrams.
     */
    const val COMMON = "common"

    /** The CSS of each alias that stands for a page style (`none` and [COMMON] are not in it). */
    val css: Map<String, String> = mapOf(
        DEFAULT to INTELLIJ_CSS,
        INTELLIJ to INTELLIJ_CSS,
        GITHUB to GITHUB_CSS,
        GITLAB to GITLAB_CSS,
        PANDOC to PANDOC_CSS,
    )

    /** The styles that have a dark colour scheme (for `prefers-color-scheme: dark`), unlike the light-only `pandoc`. */
    val darkAware: Set<String> = setOf(DEFAULT, INTELLIJ, GITHUB, GITLAB)
}

/**
 * The `<head>` lines for the `css` template variable, in the order of its entries (so later ones can override
 * earlier ones): a built-in style (see [BuiltInStyles]) is a `<style>` element, `none` is nothing, and any other
 * entry is the URL of a stylesheet, which becomes `<link rel="stylesheet" href="...">` like in pandoc's template.
 *
 * Without any entry the `default` style is used; `none` is how to get no style at all.
 *
 * The `common` style comes first, in front of all other entries (so they can override it), unless the list has `none`
 * or `common` itself: `none` turns it off together with the default style (`css=none, css=my.css` is only `my.css`),
 * and an explicit `common` is where the list wants it.
 */
internal fun styleTags(context: TemplateContext): List<String> {
    val entries = styleEntries(context)
    val withCommon = if (BuiltInStyles.NONE in entries || BuiltInStyles.COMMON in entries) entries else listOf(BuiltInStyles.COMMON) + entries
    return withCommon.flatMap { entry ->
        when (entry) {
            BuiltInStyles.NONE -> emptyList()
            BuiltInStyles.COMMON -> styleElement(COMMON_CSS)
            in BuiltInStyles.css -> styleElement(BuiltInStyles.css.getValue(entry))
            else -> listOf("""    <link rel="stylesheet" href="${entry.escapeHtml()}">""")
        }
    }
}

private fun styleElement(css: String): List<String> =
    listOf("    <style>") + css.trim('\n').lines().map { if (it.isEmpty()) it else "      $it" } + "    </style>"

/** The entries of the `css` variable, or the `default` style if there are none (without the implicit `common`, see [styleTags]). */
internal fun styleEntries(context: TemplateContext): List<String> =
    (context["css"]?.let(::cssEntries) ?: emptyList()).ifEmpty { listOf(BuiltInStyles.DEFAULT) }

/** The non-blank entries of [value], a single value or a list, trimmed. */
private fun cssEntries(value: TemplateValue): List<String> =
    (if (value is TemplateValue.Items) value.values.flatMap(::cssEntries) else listOf(value.asText().trim()))
        .filter { it.isNotEmpty() }
