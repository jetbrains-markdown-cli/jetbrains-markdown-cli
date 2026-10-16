import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.flavours.MarkdownFlavourDescriptor
import org.intellij.markdown.flavours.commonmark.CommonMarkFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.flavours.space.SFMFlavourDescriptor
import org.intellij.markdown.html.GeneratingProvider
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.CancellationToken
import org.intellij.markdown.parser.LinkMap
import org.intellij.markdown.parser.MarkdownParser
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.html.DUMMY_ATTRIBUTES_CUSTOMIZER
import org.intellij.markdown.html.URI

/** The Markdown flavours JetBrains/markdown ships. [cliName] is the value accepted on the command line. */
enum class Flavour(val cliName: String, internal val create: () -> MarkdownFlavourDescriptor) {
    COMMONMARK("commonmark", { CommonMarkFlavourDescriptor() }),
    GFM("gfm", { GFMFlavourDescriptor() }),
    SPACE("space", { SFMFlavourDescriptor() });

    companion object {
        /** The flavour of IntelliJ's Markdown plugin (`MarkdownDefaultFlavour` is built on GFM). */
        val DEFAULT = GFM
    }
}

/**
 * The chosen flavour, plus the heading `id`s the IntelliJ Markdown plugin generates.
 *
 * Unless [standalone], the `<body>` the library wraps the document in is dropped, leaving just the content.
 */
private class CliFlavour(
    private val standalone: Boolean,
    private val delegate: MarkdownFlavourDescriptor,
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
        if (!standalone) providers.remove(MarkdownElementTypes.MARKDOWN_FILE)
        return providers
    }
}

/**
 * Converts [markdown] to HTML: the document's content, or, if [standalone], a complete HTML document
 * (doctype, `<head>` and `<body>`) like `pandoc --standalone` produces.
 *
 * [variables] and [metadata] are the template variables of the document skeleton. Variables take
 * precedence over metadata.
 */
fun markdownToHtml(
    markdown: String,
    flavour: Flavour = Flavour.DEFAULT,
    standalone: Boolean = false,
    variables: TemplateContext = emptyMap(),
    metadata: TemplateContext = emptyMap(),
): String {
    // The parser requires LF line endings, see the JetBrains/markdown README.
    val src = markdown.replace("\r\n", "\n").replace("\r", "\n")
    val descriptor = CliFlavour(standalone, flavour.create())
    val tree = MarkdownParser(descriptor, cancellationToken = CancellationToken.NonCancellable)
        .buildMarkdownTreeFromString(src as CharSequence)
    val renderer = PrettyTagRenderer(
        HtmlGenerator.DefaultTagRenderer(DUMMY_ATTRIBUTES_CUSTOMIZER, false),
        baseLevel = if (standalone) 1 else 0,
    )
    // Every block starts with a line break; the first one is not wanted at the start of the output.
    val body = HtmlGenerator(src, tree, descriptor).generateHtml(renderer)
    if (!standalone) return body.trimStart('\n').let { if (it.isEmpty()) it else it + "\n" }
    return """
        |<!DOCTYPE html>
        |<html>
        |  <head>
        |    <meta charset="UTF-8">
        |    <title>${firstHeadingText(tree, src).escapeHtml()}</title>
        |  </head>$body
        |</html>
        |
    """.trimMargin()
}

private fun firstHeadingText(root: ASTNode, src: String): String {
    val heading = root.children.firstOrNull { it.type in headingTypes } ?: return ""
    val content = heading.children.firstOrNull {
        it.type == MarkdownTokenTypes.ATX_CONTENT || it.type == MarkdownTokenTypes.SETEXT_CONTENT
    } ?: return ""
    return content.getTextInNode(src).trim().toString()
}

private fun String.escapeHtml() = replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
