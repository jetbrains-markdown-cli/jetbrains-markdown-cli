import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.html.HtmlGenerator

private val blockTags = setOf(
    "body", "p", "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol", "li", "dl", "dt", "dd", "blockquote", "pre",
    "hr", "table", "thead", "tbody", "tr", "th", "td", "div",
)

private val rawBlockStart = Regex("^</?([a-z0-9]+)[ />]")
private val simpleTag = Regex("^<(/?)([a-z0-9]+)>$")

/**
 * Puts block level tags on their own, indented lines (like Jsoup's pretty printer does for the IDE's export)
 * and leaves everything inside `<pre>` as is, since whitespace is significant there.
 *
 * Some tags (`<pre>`, `<thead>`, `<tbody>`) are emitted by the generator as raw HTML instead of through
 * [openTag]/[closeTag], so [printHtml] has to recognize them.
 *
 * [baseLevel] is the indentation level of the outermost block tag.
 */
internal class PrettyTagRenderer(
    private val delegate: HtmlGenerator.TagRenderer,
    private val baseLevel: Int,
) : HtmlGenerator.TagRenderer {
    private class Frame(var hasBlockChild: Boolean = false)

    private val open = ArrayList<Frame>()
    private var preDepth = 0

    private fun indent(level: Int) = "\n" + "  ".repeat(baseLevel + level)

    private fun enter(name: String, html: CharSequence): CharSequence {
        open.lastOrNull()?.hasBlockChild = true
        val prefix = indent(open.size)
        open.add(Frame())
        if (name == "pre") preDepth++
        return prefix + html
    }

    private fun leave(name: String, html: CharSequence): CharSequence {
        if (name == "pre" && preDepth > 0) preDepth--
        val frame = open.removeLastOrNull() ?: return html
        return if (frame.hasBlockChild) indent(open.size) + html else html
    }

    override fun openTag(
        node: ASTNode,
        tagName: CharSequence,
        vararg attributes: CharSequence?,
        autoClose: Boolean,
    ): CharSequence {
        val name = tagName.toString()
        val tag = delegate.openTag(node, tagName, *attributes, autoClose = autoClose)
        if (preDepth > 0 || name !in blockTags) return tag
        if (autoClose) {
            open.lastOrNull()?.hasBlockChild = true
            return indent(open.size) + tag
        }
        return enter(name, tag)
    }

    override fun closeTag(tagName: CharSequence): CharSequence {
        val name = tagName.toString()
        val tag = delegate.closeTag(tagName)
        return if (preDepth > 0 || name !in blockTags) tag else leave(name, tag)
    }

    override fun printHtml(html: CharSequence): CharSequence {
        val printed = delegate.printHtml(html)
        val text = printed.toString()
        if (preDepth > 0) {
            return if ("</pre>" in text) leave("pre", printed) else printed
        }
        if (text.startsWith("<pre") && "</pre>" !in text) return enter("pre", printed)
        val match = simpleTag.matchEntire(text)
        if (match == null) {
            // A raw HTML block: start it on its own line, without the trailing newline it carries.
            val block = rawBlockStart.find(text)?.takeIf { it.groupValues[1] in blockTags } ?: return printed
            open.lastOrNull()?.hasBlockChild = true
            return indent(open.size) + text.trimEnd()
        }
        val name = match.groupValues[2]
        return when {
            name !in blockTags -> printed
            match.groupValues[1].isEmpty() -> enter(name, printed)
            else -> leave(name, printed)
        }
    }
}
