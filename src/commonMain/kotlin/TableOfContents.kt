import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.html.HtmlGenerator

/** The deepest heading level `--toc-depth` allows. */
internal const val MAX_TOC_DEPTH = 6

/** The default `--toc-depth`, like in pandoc. */
internal const val DEFAULT_TOC_DEPTH = 3

/**
 * The table of contents of pandoc's default HTML template, which comes after the title block, or an empty string.
 * As in the template, it is up to the variable `toc`:
 *
 * ```html
 * <nav id="TOC" role="doc-toc">
 *   <h2 id="toc-title">…</h2>
 *   <ul>
 *     <li><a href="#heading" id="toc-heading">Heading</a>
 *       <ul>…</ul>
 *     </li>
 *   </ul>
 * </nav>
 * ```
 *
 * The entries are only there with `--toc` ([entries] is not null; they are what pandoc calls `table-of-contents`),
 * and then `toc` is `true` unless the document or a variable set it, or there are no entries. So, as in pandoc,
 * `-V toc` or `toc: true` alone give an empty `<nav>`, `toc: false` in the metadata turns the `--toc` off, and
 * `-V toc=false` (the text `false`) does not.
 */
internal fun tableOfContents(context: TemplateContext, entries: List<TocEntry>?): String {
    val toc = context["toc"] ?: if (entries.isNullOrEmpty()) return "" else TemplateValue.Flag(true)
    if (!toc.isTruthy()) return ""
    val title = context["toc-title"]?.asText()?.takeIf { it.isNotBlank() }
    val inner = ArrayList<String>()
    if (title != null) inner += """      <h2 id="toc-title">${title.escapeHtml()}</h2>"""
    if (!entries.isNullOrEmpty()) inner += list(nest(entries), depth = 0)
    // An empty element stays on one line.
    val body = if (inner.isEmpty()) "" else "\n" + inner.joinToString("\n") + "\n    "
    return "\n    <nav id=\"TOC\" role=\"doc-toc\">$body</nav>"
}

/** A heading of the table of contents: its [level], the `id` it links to and its content as [html]. */
internal class TocEntry(val level: Int, val anchor: String, val html: String)

/**
 * The headings of [rendered] up to [depth] for the table of contents.
 *
 * The text of an entry is the heading's inline HTML, without links (a link cannot be inside the entry's link).
 */
internal fun tocEntries(rendered: Rendered, src: String, depth: Int): List<TocEntry> {
    val anchors = headingAnchors(rendered.tree, src)
    return rendered.tree.headings().mapNotNull { heading ->
        val level = headingLevel(heading)
        val anchor = anchors[heading]
        if (level > depth || anchor == null) return@mapNotNull null
        TocEntry(level, anchor, entryHtml(rendered, heading, src))
    }.toList()
}

private val linkTag = Regex("</?a(?:\\s[^>]*)?>")
private val whitespaces = Regex("\\s+")

private fun entryHtml(rendered: Rendered, heading: ASTNode, src: String): String {
    val content = heading.children.firstOrNull {
        it.type == MarkdownTokenTypes.ATX_CONTENT || it.type == MarkdownTokenTypes.SETEXT_CONTENT
    } ?: return ""
    val html = HtmlGenerator(src, content, rendered.providers).generateHtml()
    return html.replace(linkTag, "").replace(whitespaces, " ").trim()
}

private class TocNode(val entry: TocEntry, val children: MutableList<TocNode> = ArrayList())

/**
 * [entries] as a tree: a heading is nested in the closest one before it with a lower level, so a skipped level
 * (a `###` right after a `#`) does not make an empty list in between, and a document can start at any level, like in pandoc.
 */
private fun nest(entries: List<TocEntry>): List<TocNode> {
    val roots = ArrayList<TocNode>()
    val open = ArrayList<TocNode>()
    for (entry in entries) {
        while (open.isNotEmpty() && open.last().entry.level >= entry.level) open.removeLast()
        val node = TocNode(entry)
        (open.lastOrNull()?.children ?: roots) += node
        open += node
    }
    return roots
}

/** The `<ul>` of [nodes], [depth] being the number of lists around it. A `<li>` with a nested list ends on a line of its own. */
private fun list(nodes: List<TocNode>, depth: Int): List<String> {
    val pad = "      " + "    ".repeat(depth)
    val lines = ArrayList<String>()
    lines += "$pad<ul>"
    for (node in nodes) {
        val link = """<a href="#${node.entry.anchor}" id="toc-${node.entry.anchor}">${node.entry.html}</a>"""
        if (node.children.isEmpty()) {
            lines += "$pad  <li>$link</li>"
        } else {
            lines += "$pad  <li>$link"
            lines += list(node.children, depth + 1)
            lines += "$pad  </li>"
        }
    }
    lines += "$pad</ul>"
    return lines
}
