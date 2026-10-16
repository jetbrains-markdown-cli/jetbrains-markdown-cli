/** Where highlight.js is loaded from unless `highlight-url` says otherwise: the latest release of the official package. */
internal const val DEFAULT_HIGHLIGHT_URL = "https://cdn.jsdelivr.net/npm/@highlightjs/cdn-assets"

private const val DARK = "(prefers-color-scheme: dark)"
private const val NOT_DARK = "not all and $DARK"

/**
 * Highlights the code fences that have a language, in the browser: `<code class="language-kotlin">` is what
 * highlight.js looks for. Fences without a language stay plain (highlight.js would guess one), and so do languages
 * highlight.js does not know (`mermaid`, ...), without a message in the console. Runs after the deferred scripts.
 */
private const val HIGHLIGHT_SCRIPT = """document.addEventListener("DOMContentLoaded", function () {
  if (typeof hljs === "undefined") return;
  document.querySelectorAll('pre > code[class*="language-"]').forEach(function (code) {
    var match = /(?:^|\s)language-(\S+)/.exec(code.className);
    if (match && hljs.getLanguage(match[1])) hljs.highlightElement(code);
  });
});"""

/**
 * highlight.js styles give `pre code` their own box: padding, background and text colour. The built-in styles (see
 * [BuiltInStyles]) have a box for `pre` as well, so there are two of them unless one is made to give way.
 */
private enum class Box(val css: List<String>) {
    /** A style of ours (derived from `css`): the code takes the box and the colours of the `pre`. */
    PAGE(
        listOf(
            "pre code.hljs {",
            "  display: inline;",
            "  padding: 0;",
            "  overflow: visible;",
            "  color: inherit;",
            "  background: transparent;",
            "}",
        ),
    ),

    /** A style that was asked for, which may well be dark on a light page: its box is the one that is seen. */
    STYLE(listOf("pre:has(> code.hljs) {", "  padding: 0;", "  background: none;", "}")),

    /** Without a built-in style there is no second box. */
    NONE(emptyList()),
}

/** What `highlight` and its companion variables ask for, see [highlightStyleTags]. */
private class Highlighting(
    val script: String,
    val light: String,
    val dark: String?,
    val box: Box,
    val languages: List<String>,
)

/**
 * The `<link>`s of the highlight.js style, for the variable `highlight` (`true`; nothing is highlighted otherwise).
 * These come before the styles of [styleTags], which can therefore override them, and are followed by the rules
 * that make highlight.js and a built-in style share the box of a code block (see [Box]).
 *
 * The variables:
 * - `highlight-url`: where highlight.js comes from, by default [DEFAULT_HIGHLIGHT_URL]. Either the directory that
 *   has `highlight.min.js`, `styles/NAME.min.css` and `languages/NAME.min.js` (like the official `@highlightjs/cdn-assets`
 *   package, cdnjs and unpkg), or the URL of the script itself, whatever it is called, with `styles/` next to it.
 * - `highlight-style`: the name of a highlight.js style (`monokai-sublime`), or the URL of a stylesheet (it ends in `.css`, or has a `://`).
 *   By default `idea` for the `default` and `intellij` styles of `css`, and `github` otherwise, for the last
 *   built-in style that is used and also if there is none (`none`).
 * - `highlight-style-dark`: the same for `prefers-color-scheme: dark`. By default `androidstudio` for `idea` and
 *   `github-dark` for `github`, as long as `highlight-style` is not set (then it is used for both), and none
 *   without a built-in style because that page is not dark.
 * - `highlight-languages`: the languages to load besides those of the script, from `languages/NAME.min.js`.
 *
 * @throws InvalidVariableException for a value of `highlight` that is not a boolean or a name that is not one
 */
internal fun highlightStyleTags(context: TemplateContext): List<String> {
    val highlighting = highlighting(context) ?: return emptyList()
    fun link(href: String, media: String?) =
        """    <link rel="stylesheet" href="${href.escapeHtml()}"${if (media == null) "" else " media=\"$media\""}>"""
    val links = if (highlighting.dark == null) listOf(link(highlighting.light, null))
    else listOf(link(highlighting.light, NOT_DARK), link(highlighting.dark, DARK))
    val box = highlighting.box.css
    // The rules are as specific as those of highlight.js, and after them; but before the styles of `css`.
    return links + if (box.isEmpty()) emptyList() else listOf("    <style>") + box.map { "      $it" } + "    </style>"
}

/** The `<script>`s that load and run highlight.js, if `highlight` is set, see [highlightStyleTags]. */
internal fun highlightScriptTags(context: TemplateContext): List<String> {
    val highlighting = highlighting(context) ?: return emptyList()
    val sources = listOf(highlighting.script) + highlighting.languages
    return sources.map { """    <script src="${it.escapeHtml()}" defer></script>""" } +
        "    <script>" + HIGHLIGHT_SCRIPT.lines().map { "      $it" } + "    </script>"
}

private fun highlighting(context: TemplateContext): Highlighting? {
    if (context["highlight"]?.asBoolean("highlight") != true) return null
    val location = scriptLocation(context["highlight-url"]?.asText(), DEFAULT_HIGHLIGHT_URL, "highlight.min.js")
    val explicitLight = styleName(context, "highlight-style")
    val explicitDark = styleName(context, "highlight-style-dark")
    val builtIn = styleEntries(context).lastOrNull { it in BuiltInStyles.css }
    val (derivedLight, derivedDark) = when (builtIn) {
        BuiltInStyles.GITHUB -> "github" to "github-dark"
        BuiltInStyles.DEFAULT, BuiltInStyles.INTELLIJ -> "idea" to "androidstudio"
        else -> "github" to null
    }
    fun href(style: String) = if (isStylesheet(style)) style else location.inDir("styles/$style.min.css")
    return Highlighting(
        script = location.script,
        light = href(explicitLight ?: derivedLight),
        dark = (explicitDark ?: if (explicitLight == null) derivedDark else null)?.let(::href),
        box = when {
            builtIn == null -> Box.NONE
            explicitLight == null && explicitDark == null -> Box.PAGE
            else -> Box.STYLE
        },
        languages = languageNames(context).map { location.inDir("languages/$it.min.js") },
    )
}

private fun styleName(context: TemplateContext, variable: String): String? {
    val name = context[variable]?.asText()?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return name.also { if (!isStylesheet(it)) requireName(variable, it, allowSlash = true) }
}

/** A style that is a stylesheet, not the name of one of highlight.js: a URL, or a path to a `.css` file. */
private fun isStylesheet(style: String) = "://" in style || withoutQuery(style).endsWith(".css")

private fun languageNames(context: TemplateContext): List<String> {
    val value = context["highlight-languages"] ?: return emptyList()
    val all = if (value is TemplateValue.Items) value.values else listOf(value)
    return all.map { it.asText().trim() }.filter { it.isNotEmpty() }.onEach { requireName("highlight-languages", it, allowSlash = false) }
}

/** A highlight.js name goes into a file name, so it has no `..` and only characters of file names of highlight.js. */
private fun requireName(variable: String, name: String, allowSlash: Boolean) {
    val valid = name.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it in "._-" || (allowSlash && it == '/') } &&
        ".." !in name && !name.startsWith("/")
    if (!valid) throw InvalidVariableException("the variable `$variable` has an invalid name: `$name`")
}
