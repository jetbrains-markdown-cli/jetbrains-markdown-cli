/**
 * The title block of pandoc's default HTML template, which starts the `<body>`, or an empty string:
 *
 * ```html
 * <header id="title-block-header">
 *   <h1 class="title">…</h1>
 *   <p class="author">…</p>
 *   <p class="date">…</p>
 * </header>
 * ```
 *
 * It is only produced for a `title`, as in pandoc; each of the `author`s and `date` are optional. The first three
 * are Markdown inlines.
 */
internal fun titleBlock(context: TemplateContext, flavour: Flavour): String {
    val title = context["title"]?.takeIf { it.asText().isNotBlank() } ?: return ""
    val lines = ArrayList<String>()
    lines += """      <h1 class="title">${inlineHtml(title, flavour)}</h1>"""
    for (author in authors(context)) lines += """      <p class="author">${inlineHtml(author, flavour)}</p>"""
    context["date"]?.takeIf { it.asText().isNotBlank() }?.let {
        lines += """      <p class="date">${inlineHtml(it, flavour)}</p>"""
    }
    return "\n    <header id=\"title-block-header\">\n" + lines.joinToString("\n") + "\n    </header>"
}

/** Puts [block] (see [titleBlock]) at the start of the `<body>` in [body], the generated `<body>` element. */
internal fun withTitleBlock(body: String, block: String): String = when {
    block.isEmpty() -> body
    "<body></body>" in body -> body.replaceFirst("<body></body>", "<body>$block\n  </body>")
    else -> body.replaceFirst("<body>", "<body>$block")
}

/** The `<meta>` lines for the `<head>`: the `author`s and the `date`, as plain text. */
internal fun metaTags(context: TemplateContext, flavour: Flavour): List<String> {
    val tags = ArrayList<String>()
    for (author in authors(context)) {
        tags += """    <meta name="author" content="${attribute(author, flavour)}">"""
    }
    context["date"]?.let { attribute(it, flavour) }?.takeIf { it.isNotEmpty() }?.let {
        tags += """    <meta name="dcterms.date" content="$it">"""
    }
    return tags
}

/**
 * The authors, like pandoc's `$for(author)$`: a list has one author per item and a map (`{name: ..., ...}`)
 * stands for its `name`. Blank authors are left out.
 */
private fun authors(context: TemplateContext): List<TemplateValue> {
    val author = context["author"] ?: return emptyList()
    val all = if (author is TemplateValue.Items) author.values else listOf(author)
    return all
        .map { if (it is TemplateValue.Fields) it.values["name"] ?: TemplateValue.Text("") else it }
        .filter { it.asText().isNotBlank() }
}

private fun attribute(value: TemplateValue, flavour: Flavour) = plainText(value, flavour).replace("\"", "&quot;")

/**
 * The Markdown [value] as inline HTML. Block level Markdown (several paragraphs, a heading, ...)
 * cannot be inserted into a `<p>` or `<h1>`, so it is reduced to plain text.
 */
private fun inlineHtml(value: TemplateValue, flavour: Flavour): String {
    val html = render(normalizeLineEndings(value.asText()), flavour, standalone = false).body.trim()
    val inner = html.removePrefix("<p>").removeSuffix("</p>")
    val singleParagraph = html.startsWith("<p>") && html.endsWith("</p>") && "<p>" !in inner
    return if (singleParagraph) inner else plainText(value, flavour)
}
