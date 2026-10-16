import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.flavours.MarkdownFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.html.GeneratingProvider
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.html.URI
import org.intellij.markdown.parser.CancellationToken
import org.intellij.markdown.parser.LinkMap
import org.intellij.markdown.parser.MarkdownParser

/** GFM, plus the heading `id`s the IntelliJ Markdown plugin generates. */
private class CliFlavour(
    private val delegate: GFMFlavourDescriptor = GFMFlavourDescriptor(),
) : MarkdownFlavourDescriptor by delegate {
    override fun createHtmlGeneratingProviders(linkMap: LinkMap, baseURI: URI?): Map<IElementType, GeneratingProvider> {
        val providers = HashMap(delegate.createHtmlGeneratingProviders(linkMap, baseURI))
        providers[MarkdownElementTypes.ATX_1] = AnchoredHeadingProvider("h1")
        providers[MarkdownElementTypes.ATX_2] = AnchoredHeadingProvider("h2")
        providers[MarkdownElementTypes.ATX_3] = AnchoredHeadingProvider("h3")
        providers[MarkdownElementTypes.ATX_4] = AnchoredHeadingProvider("h4")
        providers[MarkdownElementTypes.ATX_5] = AnchoredHeadingProvider("h5")
        providers[MarkdownElementTypes.ATX_6] = AnchoredHeadingProvider("h6")
        providers[MarkdownElementTypes.SETEXT_1] = AnchoredHeadingProvider("h1")
        providers[MarkdownElementTypes.SETEXT_2] = AnchoredHeadingProvider("h2")
        return providers
    }
}

fun markdownToHtml(markdown: String): String {
    // The parser requires LF line endings, see the JetBrains/markdown README.
    val src = markdown.replace("\r\n", "\n").replace("\r", "\n")
    val flavour = CliFlavour()
    val tree = MarkdownParser(flavour, cancellationToken = CancellationToken.NonCancellable)
        .buildMarkdownTreeFromString(src as CharSequence)
    return HtmlGenerator(src, tree, flavour).generateHtml()
}
