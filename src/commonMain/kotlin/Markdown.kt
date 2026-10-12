import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.CancellationToken
import org.intellij.markdown.parser.MarkdownParser

fun markdownToHtml(markdown: String): String {
    // The parser requires LF line endings, see the JetBrains/markdown README.
    val src = markdown.replace("\r\n", "\n").replace("\r", "\n")
    val flavour = GFMFlavourDescriptor()
    val tree = MarkdownParser(flavour, cancellationToken = CancellationToken.NonCancellable)
        .buildMarkdownTreeFromString(src as CharSequence)
    return HtmlGenerator(src, tree, flavour).generateHtml()
}
