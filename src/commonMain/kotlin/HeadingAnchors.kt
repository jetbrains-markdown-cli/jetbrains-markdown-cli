import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getParentOfType
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.html.SimpleTagProvider
import org.intellij.markdown.html.entities.Entities

/**
 * Port of the IntelliJ Markdown plugin's heading anchors (`HeaderGeneratingProvider`, `HeaderAnchorCache`
 * and `MarkdownHeader.replaceEntities`), so the `id`s match the ones of the IDE's preview/export.
 */
internal val headingTypes = listOf(
    MarkdownElementTypes.ATX_1, MarkdownElementTypes.ATX_2, MarkdownElementTypes.ATX_3,
    MarkdownElementTypes.ATX_4, MarkdownElementTypes.ATX_5, MarkdownElementTypes.ATX_6,
    MarkdownElementTypes.SETEXT_1, MarkdownElementTypes.SETEXT_2,
)

internal class AnchoredHeadingProvider(tagName: String) : SimpleTagProvider(tagName) {
    private var file: ASTNode? = null
    private var anchors: Map<ASTNode, String> = emptyMap()

    override fun openTag(visitor: HtmlGenerator.HtmlGeneratingVisitor, text: String, node: ASTNode) {
        val anchor = anchorOf(node, text) ?: return super.openTag(visitor, text, node)
        visitor.consumeTagOpen(node, tagName, "id=\"$anchor\"")
    }

    private fun anchorOf(node: ASTNode, text: String): String? {
        val root = node.getParentOfType(MarkdownElementTypes.MARKDOWN_FILE) ?: return null
        if (file !== root) {
            file = root
            anchors = headingAnchors(root, text)
        }
        return anchors[node]
    }
}

/** The `id` of every heading below [root] (the whole document), in the plugin's way, see [AnchoredHeadingProvider]. */
internal fun headingAnchors(root: ASTNode, text: String): Map<ASTNode, String> {
    val result = HashMap<ASTNode, String>()
    val occurrences = HashMap<String, Int>()
    for (heading in root.headings()) {
        val raw = rawAnchorText(heading, text) ?: continue
        val occurrence = occurrences[raw] ?: 0
        occurrences[raw] = occurrence + 1
        result[heading] = if (occurrence == 0) raw else "$raw-$occurrence"
    }
    return result
}

/** The headings below (and including) this node, in document order. */
internal fun ASTNode.headings(): Sequence<ASTNode> = sequence {
    if (type in headingTypes) yield(this@headings)
    for (child in children) yieldAll(child.headings())
}

/** The level (1 to 6) of a heading node. */
internal fun headingLevel(heading: ASTNode): Int = headingTypes.indexOf(heading.type).let { if (it < 6) it + 1 else it - 5 }

// The plugin strips everything but `[\p{IsAlphabetic}\d\-_ ]` with a regex. Kotlin/Native's regex engine knows
// neither `\p{IsAlphabetic}` nor negated classes containing `\p{..}` (it silently misbehaves), so filter by
// character category instead: letters, letter numbers and combining marks come close to "alphabetic".
private fun Char.isAnchorChar(): Boolean = when (category) {
    CharCategory.UPPERCASE_LETTER, CharCategory.LOWERCASE_LETTER, CharCategory.TITLECASE_LETTER,
    CharCategory.MODIFIER_LETTER, CharCategory.OTHER_LETTER, CharCategory.LETTER_NUMBER,
    CharCategory.NON_SPACING_MARK, CharCategory.COMBINING_SPACING_MARK, CharCategory.ENCLOSING_MARK -> true
    else -> this in '0'..'9' || this == '-' || this == '_' || this == ' '
}

private val entityRegex = Regex("""&(?:([a-zA-Z0-9]+)|#([0-9]{1,8})|#[xX]([a-fA-F0-9]{1,8}));""")

private fun rawAnchorText(heading: ASTNode, text: String): String? {
    val content = heading.children.find {
        it.type == MarkdownTokenTypes.ATX_CONTENT || it.type == MarkdownTokenTypes.SETEXT_CONTENT
    } ?: return null
    val title = buildString {
        for (child in content.children.dropWhile { it.type == MarkdownTokenTypes.WHITE_SPACE }) {
            when (child.type) {
                MarkdownTokenTypes.WHITE_SPACE -> append(' ')
                MarkdownElementTypes.IMAGE -> Unit
                MarkdownElementTypes.INLINE_LINK -> appendInlineLinkText(child, text)
                else -> append(child.getTextInNode(text))
            }
        }
    }
    return replaceEntities(title).lowercase().filter { it.isAnchorChar() }.replace(" ", "-")
}

private fun StringBuilder.appendInlineLinkText(link: ASTNode, text: String) {
    val linkText = link.children.find { it.type == MarkdownElementTypes.LINK_TEXT } ?: return
    val open = linkText.children.firstOrNull()?.takeIf { it.type == MarkdownTokenTypes.LBRACKET }
    val close = linkText.children.lastOrNull()?.takeIf { it.type == MarkdownTokenTypes.RBRACKET }
    linkText.children
        .filter { it != open && it != close }
        .filter { it.type != MarkdownTokenTypes.EOL && it.type != MarkdownTokenTypes.WHITE_SPACE }
        .joinTo(this, separator = " ") { if (it.type == MarkdownElementTypes.IMAGE) "" else it.getTextInNode(text) }
}

private fun replaceEntities(text: CharSequence): String = entityRegex.replace(text) { match ->
    val groups = match.groups
    val code = when {
        groups[1] != null -> Entities.map[match.value]
        groups[2] != null -> groups[2]!!.value.toInt()
        else -> groups[3]!!.value.toInt(16)
    }
    code?.toChar()?.toString() ?: "&amp;${match.value.substring(1)}"
}
