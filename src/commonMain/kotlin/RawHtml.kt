import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.html.GeneratingProvider
import org.intellij.markdown.html.HtmlGenerator

/**
 * What the Markdown is parsed with and what is made of raw HTML in it: [flavour], and whether [rawHtml] (HTML blocks and
 * inline tags in the text) goes into the output as it is, which is what Markdown does, or is shown as text (`--no-raw-html`),
 * like pandoc's `markdown-raw_html`.
 */
internal class Dialect(val flavour: Flavour = Flavour.DEFAULT, val rawHtml: Boolean = true)

/** An inline HTML tag (`<style>`, `</p>`, ...) in the text, as text. */
internal object EscapedInlineHtmlProvider : GeneratingProvider {
    override fun processNode(visitor: HtmlGenerator.HtmlGeneratingVisitor, text: String, node: ASTNode) {
        visitor.consumeHtml(node.getTextInNode(text).toString().escapeHtml())
    }
}

/** A block of HTML, as the text of a paragraph. */
internal object EscapedHtmlBlockProvider : GeneratingProvider {
    override fun processNode(visitor: HtmlGenerator.HtmlGeneratingVisitor, text: String, node: ASTNode) {
        visitor.consumeTagOpen(node, "p")
        for (child in node.children) {
            if (child.type == MarkdownTokenTypes.EOL || child.type == MarkdownTokenTypes.HTML_BLOCK_CONTENT) {
                visitor.consumeHtml(child.getTextInNode(text).toString().escapeHtml())
            }
        }
        visitor.consumeTagClose("p")
    }
}
