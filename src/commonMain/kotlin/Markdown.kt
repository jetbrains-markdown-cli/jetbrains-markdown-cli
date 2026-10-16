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
 * [variables], [metadata] and [metadataOverrides] are the template variables of the document skeleton.
 * Variables take precedence over [metadataOverrides], which take precedence over [metadata].
 *
 * YAML metadata blocks in the document are not part of the output and, being metadata, take precedence over
 * [metadata] but not over [metadataOverrides].
 *
 * The variable `title` is the document's `<title>`; without it the text of the first heading is used. With a `title`
 * the `<body>` starts with a title block (see [titleBlock]). `author` and `date` also become `<meta>` tags, and
 * `lang` the `lang` attribute of `<html>`.
 *
 * @throws InvalidMetadataException if a YAML metadata block in [markdown] is not valid YAML
 */
fun markdownToHtml(
    markdown: String,
    flavour: Flavour = Flavour.DEFAULT,
    standalone: Boolean = false,
    variables: TemplateContext = emptyMap(),
    metadata: TemplateContext = emptyMap(),
    metadataOverrides: TemplateContext = emptyMap(),
): String {
    val (src, documentMetadata) = extractMetadataBlocks(normalizeLineEndings(markdown))
    val rendered = render(src, flavour, standalone)
    // Every block starts with a line break; the first one is not wanted at the start of the output.
    if (!standalone) return rendered.body.trimStart('\n').let { if (it.isEmpty()) it else it + "\n" }
    val context = mergeMetadata(listOf(metadata, documentMetadata, metadataOverrides, variables))
    val title = context["title"]?.let { plainText(it, flavour) }?.takeIf { it.isNotEmpty() }
        ?: firstHeadingText(rendered.tree, src).escapeHtml()
    val lang = context["lang"]?.asText()?.trim()?.takeIf { it.isNotEmpty() }
    val head = listOf("    <meta charset=\"UTF-8\">") + metaTags(context, flavour) + "    <title>$title</title>"
    return """
        |<!DOCTYPE html>
        |<html${if (lang == null) "" else " lang=\"${lang.escapeHtml()}\""}>
        |  <head>
        |${head.joinToString("\n")}
        |  </head>${withTitleBlock(rendered.body, titleBlock(context, flavour))}
        |</html>
        |
    """.trimMargin()
}

// The parser requires LF line endings, see the JetBrains/markdown README.
internal fun normalizeLineEndings(text: String) = text.replace("\r\n", "\n").replace("\r", "\n")

internal class Rendered(val tree: ASTNode, val body: String)

internal fun render(
    src: String,
    flavour: Flavour,
    standalone: Boolean,
    baseLevel: Int = if (standalone) 1 else 0,
): Rendered {
    val descriptor = CliFlavour(standalone, flavour.create())
    val tree = MarkdownParser(descriptor, cancellationToken = CancellationToken.NonCancellable)
        .buildMarkdownTreeFromString(src as CharSequence)
    val renderer = PrettyTagRenderer(
        HtmlGenerator.DefaultTagRenderer(DUMMY_ATTRIBUTES_CUSTOMIZER, false),
        baseLevel = baseLevel,
    )
    return Rendered(tree, HtmlGenerator(src, tree, descriptor).generateHtml(renderer))
}

/**
 * The HTML-escaped plain text of a title or other metadata value, which is Markdown like in pandoc: `My *title*` is `My title`.
 * The value is escaped unlike in pandoc, where `--variable` values are inserted into the template as they are.
 */
internal fun plainText(value: TemplateValue, flavour: Flavour): String {
    val html = render(normalizeLineEndings(value.asText()), flavour, standalone = false).body
    return html.replace(Regex("<[^>]*>"), "").replace(Regex("\\s+"), " ").trim()
}

private fun firstHeadingText(root: ASTNode, src: String): String {
    val heading = root.children.firstOrNull { it.type in headingTypes } ?: return ""
    val content = heading.children.firstOrNull {
        it.type == MarkdownTokenTypes.ATX_CONTENT || it.type == MarkdownTokenTypes.SETEXT_CONTENT
    } ?: return ""
    return content.getTextInNode(src).trim().toString()
}

internal fun String.escapeHtml() = replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
