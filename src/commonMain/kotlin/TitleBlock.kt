/**
 * The title block of pandoc's default HTML template, which starts the `<body>`, or an empty string:
 *
 * ```html
 * <header id="title-block-header">
 *   <h1 class="title">…</h1>
 *   <p class="subtitle">…</p>
 *   <p class="author">…</p>
 *   <p class="date">…</p>
 *   <div class="abstract">
 *     <div class="abstract-title">Abstract</div>
 *     …
 *   </div>
 * </header>
 * ```
 *
 * It is only produced for a `title`, as in pandoc; `subtitle`, each of the `author`s, `date` and `abstract` are
 * optional. The first four are Markdown inlines, the `abstract` is Markdown blocks, and `abstract-title` (default
 * "Abstract") is plain text.
 */
internal fun titleBlock(context: TemplateContext, dialect: Dialect): String {
    val title = context["title"]?.takeIf { it.asText().isNotBlank() } ?: return ""
    val lines = ArrayList<String>()
    lines += """      <h1 class="title">${inlineHtml(title, dialect)}</h1>"""
    context["subtitle"]?.takeIf { it.asText().isNotBlank() }?.let {
        lines += """      <p class="subtitle">${inlineHtml(it, dialect)}</p>"""
    }
    for (author in authors(context)) lines += """      <p class="author">${inlineHtml(author, dialect)}</p>"""
    context["date"]?.takeIf { it.asText().isNotBlank() }?.let {
        lines += """      <p class="date">${inlineHtml(it, dialect)}</p>"""
    }
    context["abstract"]?.takeIf { it.asText().isNotBlank() }?.let {
        val abstractTitle = context["abstract-title"]?.asText()?.takeIf { t -> t.isNotBlank() } ?: "Abstract"
        lines += """      <div class="abstract">"""
        lines += """        <div class="abstract-title">${abstractTitle.escapeHtml()}</div>"""
        // The renderer puts every block on its own line, indented below the <div>.
        lines += render(normalizeLineEndings(it.asText()), dialect, standalone = false, baseLevel = 4)
            .body.trim('\n')
        lines += "      </div>"
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
internal fun metaTags(context: TemplateContext, dialect: Dialect): List<String> {
    val tags = ArrayList<String>()
    for (author in authors(context)) {
        tags += """    <meta name="author" content="${attribute(author, dialect)}">"""
    }
    context["date"]?.let { attribute(it, dialect) }?.takeIf { it.isNotEmpty() }?.let {
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

private fun attribute(value: TemplateValue, dialect: Dialect) = plainText(value, dialect).replace("\"", "&quot;")

/**
 * The Markdown [value] as inline HTML. Block level Markdown (several paragraphs, a heading, ...)
 * cannot be inserted into a `<p>` or `<h1>`, so it is reduced to plain text.
 */
private fun inlineHtml(value: TemplateValue, dialect: Dialect): String {
    val html = render(normalizeLineEndings(value.asText()), dialect, standalone = false).body.trim()
    val inner = html.removePrefix("<p>").removeSuffix("</p>")
    val singleParagraph = html.startsWith("<p>") && html.endsWith("</p>") && "<p>" !in inner
    return if (singleParagraph) inner else plainText(value, dialect)
}
