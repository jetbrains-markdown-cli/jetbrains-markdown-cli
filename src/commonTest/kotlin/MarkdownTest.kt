import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.parse
import com.github.ajalt.clikt.core.BadParameterValue
import com.github.ajalt.clikt.core.UsageError
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MarkdownTest {
    @Test
    fun rendersHeadingAndEmphasis() {
        val html = markdownToHtml("# Title\n\nsome *emphasis*\n")
        assertContains(html, "<h1 id=\"title\">Title</h1>")
        assertContains(html, "<em>emphasis</em>")
    }

    @Test
    fun rendersGfmTable() {
        val html = markdownToHtml("| a | b |\n|---|---|\n| 1 | 2 |\n")
        assertContains(html, "<table>")
    }

    @Test
    fun flavoursDifferInGfmExtensions() {
        val src = "~~gone~~\n\n| a |\n|---|\n| 1 |\n"
        val commonMark = markdownToHtml(src, Flavour.COMMONMARK)
        assertFalse("<table>" in commonMark, commonMark)
        assertFalse("user-del" in commonMark, commonMark)
        assertContains(markdownToHtml(src, Flavour.GFM), "<table>")
        assertContains(markdownToHtml(src, Flavour.GFM), "<span class=\"user-del\">gone</span>")
        assertContains(markdownToHtml(src, Flavour.SPACE), "<table>")
    }

    @Test
    fun everyFlavourGeneratesHeadingIds() {
        for (flavour in Flavour.entries) {
            assertContains(markdownToHtml("# Hello\n\nWorld\n-----\n", flavour), "<h1 id=\"hello\">Hello</h1>")
            assertContains(markdownToHtml("# Hello\n\nWorld\n-----\n", flavour), "<h2 id=\"world\">World</h2>")
        }
    }

    @Test
    fun everyFlavourSupportsStandalone() {
        for (flavour in Flavour.entries) {
            assertFalse("<body>" in markdownToHtml("# Hello\n", flavour), flavour.name)
            assertContains(markdownToHtml("# Hello\n", flavour, standalone = true), "  <body>\n    <h1 id=\"hello\">")
        }
    }

    @Test
    fun crlfAndLfProduceSameOutput() {
        val lf = "# Title\n\n- a\n- b\n"
        assertEquals(markdownToHtml(lf), markdownToHtml(lf.replace("\n", "\r\n")))
    }

    @Test
    fun fragmentHasNoDocumentSkeleton() {
        val html = markdownToHtml("# Title\n\ntext\n")
        assertEquals("<h1 id=\"title\">Title</h1>\n<p>text</p>\n", html)
        assertEquals("", markdownToHtml(""))
    }

    @Test
    fun standaloneWrapsBodyInHtmlSkeleton() {
        val html = markdownToHtml("# A & B\n\ntext\n", standalone = true)
        assertTrue(html.startsWith("<!DOCTYPE html>\n<html>\n  <head>\n"), html)
        assertContains(html, "<meta charset=\"UTF-8\">")
        assertContains(html, "<title>A &amp; B</title>")
        assertContains(html, "  <body>\n    <h1 id=\"a--b\">A &amp; B</h1>\n    <p>text</p>\n  </body>\n")
        assertTrue(html.endsWith("  </body>\n</html>\n"), html)
    }

    @Test
    fun headingIdsMatchIntelliJAnchors() {
        val html = markdownToHtml(
            "# jetbrains-markdown-cli\n\n## Foo & Bar: `x`!\n\n## Foo\n\n## Foo\n\nSetext\n------\n"
        )
        assertContains(html, "<h1 id=\"jetbrains-markdown-cli\">")
        assertContains(html, "<h2 id=\"foo--bar-x\">")
        assertContains(html, "<h2 id=\"foo\">")
        assertContains(html, "<h2 id=\"foo-1\">")
        assertContains(html, "<h2 id=\"setext\">")
    }

    @Test
    fun headingIdKeepsNonAsciiLetters() {
        assertContains(markdownToHtml("# Grüße_1\n"), "<h1 id=\"grüße_1\">")
    }

    @Test
    fun indentsBlocksOnSeparateLines() {
        val src = "- a\n- b\n\n| a |\n|---|\n| 1 |\n"
        val html = markdownToHtml(src)
        assertContains(html, "<ul>\n  <li>a</li>\n  <li>b</li>\n</ul>\n")
        assertContains(html, "<table>\n  <thead>\n    <tr>\n      <th>a</th>\n")
        val document = markdownToHtml(src, standalone = true)
        assertContains(document, "    <ul>\n      <li>a</li>\n      <li>b</li>\n    </ul>\n")
        assertContains(document, "    <table>\n      <thead>\n        <tr>\n          <th>a</th>\n")
    }

    @Test
    fun keepsCodeBlockContentUntouched() {
        val src = "```sh\nfoo\n  bar\n```\n"
        val html = markdownToHtml(src)
        assertEquals("<pre><code class=\"language-sh\">foo\n  bar\n</code></pre>\n", html)
        val document = markdownToHtml(src, standalone = true)
        assertContains(document, "    <pre><code class=\"language-sh\">foo\n  bar\n</code></pre>\n")
        assertFalse(document.contains("</code>\n"), document)
    }
}

class TemplateVariablesTest {
    private fun text(value: String) = TemplateValue.Text(value)

    @Test
    fun parsesKeyAndValue() {
        assertEquals("title" to text("Hello"), parseVariable("title=Hello"))
        assertEquals("title" to text("Hello"), parseVariable("title:Hello"))
        assertEquals("title" to text(""), parseVariable("title="))
    }

    @Test
    fun splitsAtTheFirstSeparator() {
        assertEquals("a" to text("b=c:d"), parseVariable("a=b=c:d"))
        assertEquals("a" to text("b=c"), parseVariable("a:b=c"))
    }

    @Test
    fun variableWithoutValueIsTrue() {
        assertEquals("draft" to TemplateValue.Flag(true), parseVariable("draft"))
    }

    @Test
    fun rejectsEmptyName() {
        assertFailsWith<IllegalArgumentException> { parseVariable("=value") }
        assertFailsWith<IllegalArgumentException> { parseVariable("") }
    }

    @Test
    fun repeatedVariableBecomesList() {
        val context = variablesOf(listOf(parseVariable("a=1"), parseVariable("b=x"), parseVariable("a=2"), parseVariable("a=3")))
        assertEquals(text("x"), context["b"])
        assertEquals(TemplateValue.Items(listOf(text("1"), text("2"), text("3"))), context["a"])
    }
}

class MetadataTest {
    private fun text(value: String) = TemplateValue.Text(value)

    @Test
    fun parsesMappingIntoTemplateValues() {
        val fields = parseYamlFields(
            """
            |title: A Title
            |draft: true
            |number: 1.50
            |nothing:
            |author:
            |  - A
            |  - B
            |meta:
            |  key: value
            """.trimMargin()
        )
        assertEquals(text("A Title"), fields["title"])
        assertEquals(TemplateValue.Flag(true), fields["draft"])
        assertEquals(text("1.50"), fields["number"])
        assertEquals(text(""), fields["nothing"])
        assertEquals(TemplateValue.Items(listOf(text("A"), text("B"))), fields["author"])
        assertEquals(TemplateValue.Fields(mapOf("key" to text("value"))), fields["meta"])
    }

    @Test
    fun quotedScalarsStayText() {
        val fields = parseYamlFields("a: 'true'\nb: \"null\"\nc: no\n")
        assertEquals(text("true"), fields["a"])
        assertEquals(text("null"), fields["b"])
        assertEquals(text("no"), fields["c"])
    }

    @Test
    fun emptyYamlHasNoFields() {
        assertEquals(emptyMap(), parseYamlFields(""))
        assertEquals(emptyMap(), parseYamlFields("# just a comment\n"))
    }

    @Test
    fun ignoresByteOrderMark() {
        assertEquals(text("x"), parseYamlFields("\uFEFFtitle: x")["title"])
    }

    @Test
    fun rejectsInvalidYamlAndNonMappings() {
        assertFailsWith<IllegalArgumentException> { parseYamlFields("title: [a") }
        assertFailsWith<IllegalArgumentException> { parseYamlFields("- a\n- b\n") }
        assertFailsWith<IllegalArgumentException> { parseYamlFields("just text") }
        assertFailsWith<IllegalArgumentException> { parseYamlFields("? [a]\n: b\n") }
    }

    @Test
    fun rejectsRecursiveStructures() {
        assertFailsWith<IllegalArgumentException> { parseYamlFields("a: &x [*x]") }
    }

    @Test
    fun laterLayersReplaceTopLevelFields() {
        val merged = mergeMetadata(
            listOf(
                mapOf("title" to text("one"), "author" to TemplateValue.Items(listOf(text("a")))),
                mapOf("title" to text("two")),
            )
        )
        assertEquals(text("two"), merged["title"])
        assertEquals(TemplateValue.Items(listOf(text("a"))), merged["author"])
    }
}

class TitleTest {
    private fun text(value: String) = TemplateValue.Text(value)

    private fun title(
        markdown: String = "# Heading\n",
        variables: TemplateContext = emptyMap(),
        metadata: TemplateContext = emptyMap(),
        metadataOverrides: TemplateContext = emptyMap(),
    ): String {
        val html = markdownToHtml(markdown, standalone = true, variables = variables, metadata = metadata, metadataOverrides = metadataOverrides)
        return html.substringAfter("<title>").substringBefore("</title>")
    }

    @Test
    fun fallsBackToFirstHeading() {
        assertEquals("Heading", title())
        assertEquals("", title("text\n"))
    }

    @Test
    fun titleVariableIsTheDocumentTitle() {
        assertEquals("Mine", title(variables = mapOf("title" to text("Mine"))))
        assertEquals("Mine", title(metadata = mapOf("title" to text("Mine"))))
        assertEquals("Mine", title("---\ntitle: Mine\n---\n\n# Heading\n"))
    }

    @Test
    fun titleDoesNotChangeTheFragment() {
        assertEquals("<h1 id=\"heading\">Heading</h1>\n", markdownToHtml("# Heading\n", variables = mapOf("title" to text("Mine"))))
    }

    @Test
    fun variablesBeatMetadataOverridesBeatDocumentBeatMetadata() {
        val document = "---\ntitle: document\n---\n"
        val metadata = mapOf("title" to text("metadata"))
        val overrides = mapOf("title" to text("overrides"))
        val variables = mapOf("title" to text("variables"))
        assertEquals("metadata", title(metadata = metadata))
        assertEquals("document", title(document, metadata = metadata))
        assertEquals("overrides", title(document, metadata = metadata, metadataOverrides = overrides))
        assertEquals("variables", title(document, metadata = metadata, metadataOverrides = overrides, variables = variables))
    }

    @Test
    fun titleIsMarkdownReducedToPlainText() {
        assertEquals("My title with a link", title(variables = mapOf("title" to text("My *title* with [a link](https://example.com)"))))
        assertEquals("a &lt; b", title(variables = mapOf("title" to text("`a < b`"))))
    }

    @Test
    fun titleIsEscaped() {
        assertEquals("A &amp; B &lt; c", title(variables = mapOf("title" to text("A & B < c"))))
        assertEquals("&lt;/title&gt;&lt;script&gt;", title(variables = mapOf("title" to text("\\</title>\\<script>"))))
    }

    @Test
    fun multiLineAndListTitles() {
        assertEquals("one two", title(variables = mapOf("title" to text("one\ntwo"))))
        assertEquals("onetwo", title(variables = mapOf("title" to TemplateValue.Items(listOf(text("one"), text("two"))))))
        assertEquals("true", title(variables = mapOf("title" to TemplateValue.Flag(true))))
    }

    @Test
    fun blankTitleFallsBackToFirstHeading() {
        assertEquals("Heading", title(variables = mapOf("title" to text(""))))
        assertEquals("Heading", title("---\ntitle:\n---\n\n# Heading\n"))
        assertEquals("Heading", title(variables = mapOf("title" to TemplateValue.Flag(false))))
    }

    @Test
    fun titleDoesNotDisturbHeadingIds() {
        val html = markdownToHtml("# Same\n\n# Same\n", standalone = true, variables = mapOf("title" to text("# Same")))
        assertContains(html, "<h1 id=\"same\">Same</h1>")
        assertContains(html, "<h1 id=\"same-1\">Same</h1>")
    }
}

class SkeletonVariablesTest {
    private fun text(value: String) = TemplateValue.Text(value)

    private fun html(markdown: String = "# Heading\n", vararg variables: Pair<String, TemplateValue>) =
        markdownToHtml(markdown, standalone = true, variables = mapOf(*variables))

    @Test
    fun langIsAnAttributeOfHtml() {
        assertTrue(html(variables = arrayOf("lang" to text("de-CH"))).startsWith("<!DOCTYPE html>\n<html lang=\"de-CH\">\n"))
        assertContains(html(variables = arrayOf("lang" to text("\"><script>"))), "<html lang=\"&quot;&gt;&lt;script&gt;\">")
        assertTrue(html(variables = arrayOf("lang" to text(" "))).startsWith("<!DOCTYPE html>\n<html>\n"))
    }

    @Test
    fun titleStartsTheBodyWithATitleBlock() {
        assertContains(
            html(variables = arrayOf("title" to text("My *title*"))),
            "  <body>\n    <header id=\"title-block-header\">\n      <h1 class=\"title\">My <em>title</em></h1>\n" +
                "    </header>\n    <h1 id=\"heading\">Heading</h1>\n  </body>",
        )
    }

    @Test
    fun titleBlockInEmptyDocument() {
        assertContains(
            markdownToHtml("", standalone = true, variables = mapOf("title" to text("T"))),
            "  <body>\n    <header id=\"title-block-header\">\n      <h1 class=\"title\">T</h1>\n    </header>\n  </body>",
        )
    }

    @Test
    fun noTitleBlockWithoutTitleVariable() {
        val html = html(variables = arrayOf("author" to text("A"), "date" to text("D"), "subtitle" to text("S"), "abstract" to text("X")))
        assertFalse("<header" in html, html)
        assertContains(html, "  <body>\n    <h1 id=\"heading\">")
    }

    @Test
    fun blockMarkdownInTheTitleIsReducedToText() {
        assertContains(html(variables = arrayOf("title" to text("# Same"))), "<h1 class=\"title\">Same</h1>")
    }

    @Test
    fun titleBlockHasAuthors() {
        assertContains(
            html(
                "# Heading\n",
                "title" to text("T"),
                "author" to TemplateValue.Items(listOf(text("Ann *A*"), TemplateValue.Fields(mapOf("name" to text("Bob"))), text(""))),
            ),
            """
            |    <header id="title-block-header">
            |      <h1 class="title">T</h1>
            |      <p class="author">Ann <em>A</em></p>
            |      <p class="author">Bob</p>
            |    </header>
            |    <h1 id="heading">Heading</h1>
            """.trimMargin(),
        )
    }

    @Test
    fun authorsFromYamlMetadata() {
        val html = markdownToHtml("---\ntitle: T\nauthor:\n  - A\n  - B\n---\n\ntext\n", standalone = true)
        assertContains(html, "<p class=\"author\">A</p>\n      <p class=\"author\">B</p>")
    }

    @Test
    fun authorsAreMetaTags() {
        val document = html(variables = arrayOf("author" to TemplateValue.Items(listOf(text("Ann *A*"), text("Bob \"B\"")))))
        assertContains(
            document,
            "    <meta charset=\"UTF-8\">\n    <meta name=\"author\" content=\"Ann A\">\n" +
                "    <meta name=\"author\" content=\"Bob &quot;B&quot;\">\n    <title>Heading</title>",
        )
        assertFalse("<meta name=\"author\"" in html(), "no author, no meta")
    }

    @Test
    fun titleBlockHasDate() {
        assertContains(
            html(variables = arrayOf("title" to text("T"), "date" to text("2026 *and* 2027"))),
            "      <h1 class=\"title\">T</h1>\n      <p class=\"date\">2026 <em>and</em> 2027</p>\n    </header>",
        )
    }

    @Test
    fun blockMarkdownInTheDateIsReducedToText() {
        assertContains(html(variables = arrayOf("title" to text("T"), "date" to text("a\n\nb"))), "<p class=\"date\">a b</p>")
    }

    @Test
    fun dateIsAMetaTag() {
        assertContains(
            html(variables = arrayOf("date" to text("2026-01-02"))),
            "    <meta charset=\"UTF-8\">\n    <meta name=\"dcterms.date\" content=\"2026-01-02\">\n    <title>Heading</title>",
        )
        assertFalse("dcterms" in html())
    }

    @Test
    fun titleBlockHasSubtitle() {
        assertContains(
            html(variables = arrayOf("title" to text("T"), "subtitle" to text("A *sub* & title"), "author" to text("Ann"))),
            "      <h1 class=\"title\">T</h1>\n      <p class=\"subtitle\">A <em>sub</em> &amp; title</p>\n      <p class=\"author\">Ann</p>",
        )
    }

    @Test
    fun titleBlockHasAbstract() {
        assertContains(
            html(variables = arrayOf("title" to text("T"), "abstract" to text("One *two*.\n\n- three"))),
            """
            |      <h1 class="title">T</h1>
            |      <div class="abstract">
            |        <div class="abstract-title">Abstract</div>
            |        <p>One <em>two</em>.</p>
            |        <ul>
            |          <li>three</li>
            |        </ul>
            |      </div>
            |    </header>
            """.trimMargin(),
        )
    }

    @Test
    fun abstractTitleCanBeChanged() {
        assertContains(
            html(variables = arrayOf("title" to text("T"), "abstract" to text("x"), "abstract-title" to text("Summary & more"))),
            "<div class=\"abstract-title\">Summary &amp; more</div>",
        )
    }

    @Test
    fun titleBlockFieldsAreInPandocsOrder() {
        val document = html(
            variables = arrayOf(
                "abstract" to text("X"),
                "date" to text("D"),
                "author" to text("A"),
                "subtitle" to text("S"),
                "title" to text("T"),
            ),
        )
        val classes = Regex("class=\"(title|subtitle|author|date|abstract)\"").findAll(document).map { it.groupValues[1] }.toList()
        assertEquals(listOf("title", "subtitle", "author", "date", "abstract"), classes)
    }

    @Test
    fun nothingChangesWithoutStandalone() {
        val all = mapOf("title" to text("T"), "subtitle" to text("S"), "author" to text("A"), "date" to text("D"), "lang" to text("en"), "abstract" to text("X"))
        assertEquals("<h1 id=\"heading\">Heading</h1>\n", markdownToHtml("# Heading\n", variables = all))
    }
}

class StyleVariableTest {
    private fun text(value: String) = TemplateValue.Text(value)

    private fun items(vararg values: String) = TemplateValue.Items(values.map(::text))

    private fun html(css: TemplateValue? = null, markdown: String = "# Heading\n") =
        markdownToHtml(markdown, standalone = true, variables = if (css == null) emptyMap() else mapOf("css" to css))

    private fun style(document: String) = document.substringAfter("<style>", "").substringBefore("</style>")

    @Test
    fun defaultStyleIsTheIntelliJOne() {
        val document = html()
        assertContains(document, "    <title>Heading</title>\n    <style>\n")
        assertContains(document, "\n    </style>\n  </head>")
        assertContains(style(document), "      body {\n")
        assertEquals(style(html()), style(html(text("default"))))
        assertEquals(style(html(text("intellij"))), style(html(text("default"))))
    }

    @Test
    fun blankCssIsTheDefaultStyle() {
        assertEquals(style(html()), style(html(text(""))))
        assertEquals(style(html()), style(html(text("  "))))
        assertEquals(style(html()), style(html(TemplateValue.Items(emptyList()))))
        assertEquals(style(html()), style(html(TemplateValue.Flag(false))))
    }

    @Test
    fun noneHasNoStyleAtAll() {
        val document = html(text("none"))
        assertFalse("<style" in document, document)
        assertFalse("<link" in document, document)
        assertContains(document, "<title>Heading</title>\n  </head>")
    }

    @Test
    fun githubStyleDiffersFromIntelliJ() {
        val github = style(html(text("github")))
        assertContains(github, "max-width: 980px;")
        assertContains(github, "github-markdown-css")
        assertFalse("JetBrains Mono" in github)
        assertContains(style(html(text("intellij"))), "JetBrains Mono")
    }

    @Test
    fun builtInStylesAreSelfContained() {
        for (alias in listOf("default", "intellij", "github")) {
            val css = style(html(text(alias)))
            assertTrue(css.isNotBlank(), alias)
            assertEquals(css.count { it == '{' }, css.count { it == '}' }, alias)
            assertFalse("<link" in html(text(alias)), alias)
            // The IDE's classes for markup this tool generates.
            for (selector in listOf(".task-list-item", "span.user-del", "pre code", "header#title-block-header")) {
                assertContains(css, selector, message = alias)
            }
        }
    }

    @Test
    fun otherValuesAreLinkedStylesheets() {
        assertContains(html(text("style.css")), "    <title>Heading</title>\n    <link rel=\"stylesheet\" href=\"style.css\">\n  </head>")
        assertFalse("<style" in html(text("style.css")))
        assertContains(html(text("https://example.com/a.css?x=1&y=\"2\"")), "href=\"https://example.com/a.css?x=1&amp;y=&quot;2&quot;\"")
        assertContains(html(text("  padded.css ")), "href=\"padded.css\"")
    }

    @Test
    fun aliasesAreExactNames() {
        assertContains(html(text("GitHub")), "<link rel=\"stylesheet\" href=\"GitHub\">")
        assertContains(html(text("./github")), "<link rel=\"stylesheet\" href=\"./github\">")
    }

    @Test
    fun aListKeepsItsOrder() {
        val document = html(items("github", "a.css", "none", "b.css"))
        val order = Regex("<style>|href=\"([^\"]+)\"").findAll(document).map { it.groupValues[1].ifEmpty { "style" } }.toList()
        assertEquals(listOf("style", "a.css", "b.css"), order)
        assertContains(document, "max-width: 980px;")
    }

    @Test
    fun noneOnlyDisablesTheDefaultStyleWhenAlone() {
        val document = html(items("none", "a.css"))
        assertFalse("<style" in document)
        assertContains(document, "href=\"a.css\"")
    }

    @Test
    fun noStylesWithoutStandalone() {
        assertEquals("<h1 id=\"heading\">Heading</h1>\n", markdownToHtml("# Heading\n", variables = mapOf("css" to text("github"))))
    }

    @Test
    fun styleDoesNotChangeTheBody() {
        val body = { document: String -> document.substringAfter("<body>").substringBefore("</body>") }
        assertEquals(body(html(text("none"))), body(html(text("github"))))
        assertEquals(body(html(text("none"))), body(html()))
    }
}

class HighlightVariableTest {
    private fun text(value: String) = TemplateValue.Text(value)

    private fun items(vararg values: String) = TemplateValue.Items(values.map(::text))

    private val on = "highlight" to TemplateValue.Flag(true)

    private fun html(vararg variables: Pair<String, TemplateValue>, markdown: String = "# Heading\n\n```kotlin\nval x = 1\n```\n") =
        markdownToHtml(markdown, standalone = true, variables = mapOf(*variables))

    private fun links(document: String) = Regex("<link [^>]*>").findAll(document).map { it.value }.toList()

    private fun scripts(document: String) = Regex("<script src=\"([^\"]+)\" defer></script>").findAll(document).map { it.groupValues[1] }.toList()

    private val base = DEFAULT_HIGHLIGHT_URL

    @Test
    fun isOffByDefault() {
        for (document in listOf(html(), html("highlight" to TemplateValue.Flag(false)), html("highlight" to text("false")), html("highlight" to text("")))) {
            assertFalse("<script" in document, document)
            assertFalse("<link" in document, document)
            assertFalse("highlight.min.js" in document, document)
        }
    }

    @Test
    fun valuesOfHighlight() {
        for (value in listOf(TemplateValue.Flag(true), text("true"), text("TRUE"), text(" true "), items("false", "true"))) {
            assertTrue("<script" in html("highlight" to value), value.toString())
        }
        assertFalse("<script" in html("highlight" to items("true", "false")))
    }

    @Test
    fun rejectsOtherValues() {
        for (value in listOf(text("ture"), text("yes"), TemplateValue.Fields(emptyMap()))) {
            val error = assertFailsWith<InvalidVariableException> { html("highlight" to value) }
            assertContains(error.message.orEmpty(), "`highlight`")
        }
    }

    @Test
    fun loadsHighlightJsFromTheOfficialPackage() {
        val document = html(on)
        assertEquals(listOf("$base/highlight.min.js"), scripts(document))
        assertEquals("https://cdn.jsdelivr.net/npm/@highlightjs/cdn-assets", base)
        assertEquals(
            listOf(
                "<link rel=\"stylesheet\" href=\"$base/styles/idea.min.css\" media=\"not all and (prefers-color-scheme: dark)\">",
                "<link rel=\"stylesheet\" href=\"$base/styles/androidstudio.min.css\" media=\"(prefers-color-scheme: dark)\">",
            ),
            links(document),
        )
    }

    @Test
    fun findsTheFencesOfTheGeneratedMarkup() {
        // The markup the script looks for: the class of the <code> in a <pre>.
        assertContains(html(on), "<pre><code class=\"language-kotlin\">")
        assertContains(html(on), "pre > code[class*=\"language-\"]")
        assertContains(html(on), "hljs.getLanguage(match[1])")
        assertContains(html(on), "hljs.highlightElement(code)")
    }

    @Test
    fun scriptsComeAfterTheDeferredFiles() {
        val document = html(on, "highlight-languages" to items("dockerfile", "nginx"))
        assertEquals(listOf("$base/highlight.min.js", "$base/languages/dockerfile.min.js", "$base/languages/nginx.min.js"), scripts(document))
        assertTrue(document.indexOf("nginx.min.js") < document.indexOf("DOMContentLoaded"))
        assertContains(html(on, "highlight-languages" to text("go")), "$base/languages/go.min.js")
    }

    @Test
    fun styleFollowsTheBuiltInStyle() {
        fun styles(vararg variables: Pair<String, TemplateValue>) = links(html(on, *variables)).map { it.substringAfter("styles/").substringBefore(".min.css") }
        assertEquals(listOf("idea", "androidstudio"), styles())
        assertEquals(listOf("idea", "androidstudio"), styles("css" to text("default")))
        assertEquals(listOf("idea", "androidstudio"), styles("css" to text("intellij")))
        assertEquals(listOf("github", "github-dark"), styles("css" to text("github")))
        // The last built-in style wins the cascade, so it decides.
        assertEquals(listOf("github", "github-dark"), styles("css" to items("intellij", "github")))
        assertEquals(listOf("idea", "androidstudio"), styles("css" to items("github", "intellij", "none")))
    }

    @Test
    fun withoutABuiltInStyleThePageIsLightAndGithub() {
        assertEquals(listOf("<link rel=\"stylesheet\" href=\"$base/styles/github.min.css\">"), links(html(on, "css" to text("none"))))
        // The stylesheets of the page are linked after the one of highlight.js; none of them is derived from.
        val custom = links(html(on, "css" to items("none", "my.css")))
        assertEquals(listOf("$base/styles/github.min.css", "my.css"), custom.map { it.substringAfter("href=\"").substringBefore("\"") })
    }

    @Test
    fun explicitStylesReplaceTheDerivedOnes() {
        assertEquals(
            listOf("<link rel=\"stylesheet\" href=\"$base/styles/monokai-sublime.min.css\">"),
            links(html(on, "highlight-style" to text("monokai-sublime"))),
        )
        assertEquals(
            listOf("$base/styles/idea.min.css", "$base/styles/vs2015.min.css"),
            links(html(on, "highlight-style-dark" to text("vs2015"))).map { it.substringAfter("href=\"").substringBefore("\"") },
        )
        val both = links(html(on, "highlight-style" to text("base16/solarized-light"), "highlight-style-dark" to text("base16/solarized-dark")))
        assertContains(both[0], "$base/styles/base16/solarized-light.min.css")
        assertContains(both[0], "media=\"not all and (prefers-color-scheme: dark)\"")
        assertContains(both[1], "$base/styles/base16/solarized-dark.min.css")
        assertContains(both[1], "media=\"(prefers-color-scheme: dark)\"")
        assertEquals(1, links(html(on, "highlight-style" to text("github-dark"), "css" to text("github"))).size)
    }

    @Test
    fun aStyleMayBeAStylesheet() {
        assertContains(html(on, "highlight-style" to text("themes/mine.css")), "<link rel=\"stylesheet\" href=\"themes/mine.css\">")
        assertContains(html(on, "highlight-style" to text("https://example.com/a.css?x=1&y=2")), "href=\"https://example.com/a.css?x=1&amp;y=2\"")
        assertContains(html(on, "highlight-style" to text("https://example.com/theme")), "href=\"https://example.com/theme\"")
    }

    @Test
    fun urlIsADirectoryOrTheScript() {
        fun urls(url: String, vararg more: Pair<String, TemplateValue>): List<String> {
            val document = html(on, "highlight-url" to text(url), "highlight-style" to text("monokai-sublime"), *more)
            return scripts(document) + links(document).map { it.substringAfter("href=\"").substringBefore("\"") }
        }
        assertEquals(listOf("vendor/hljs/highlight.min.js", "vendor/hljs/styles/monokai-sublime.min.css"), urls("vendor/hljs"))
        assertEquals(listOf("https://cdnjs.example/hljs/highlight.min.js", "https://cdnjs.example/hljs/styles/monokai-sublime.min.css"), urls("https://cdnjs.example/hljs/"))
        assertEquals(
            listOf(
                "https://example.com/lib/hljs.js",
                "https://example.com/lib/styles/monokai-sublime.min.css",
            ),
            urls("https://example.com/lib/hljs.js"),
        )
        assertEquals(listOf("/hljs.js", "/styles/monokai-sublime.min.css"), urls("/hljs.js"))
        assertEquals(listOf("hljs.js", "styles/monokai-sublime.min.css"), urls("hljs.js"))
        assertEquals(listOf("/a/hl.js?v=1/2", "/a/styles/monokai-sublime.min.css"), urls("/a/hl.js?v=1/2"))
        assertEquals(listOf("/highlight.min.js", "/styles/monokai-sublime.min.css"), urls("/"))
        assertEquals(listOf("$base/highlight.min.js", "$base/styles/monokai-sublime.min.css"), urls("  "))
        assertContains(html(on, "highlight-url" to text("a\"b")), "src=\"a&quot;b/highlight.min.js\"")
    }

    @Test
    fun rejectsNamesThatAreNotFileNames() {
        for (variable in listOf("highlight-style", "highlight-style-dark", "highlight-languages")) {
            for (name in listOf("../x", "a/../b", "\"><b>", "a b")) {
                val error = assertFailsWith<InvalidVariableException>("$variable $name") { html(on, variable to text(name)) }
                assertContains(error.message.orEmpty(), "`$variable`")
            }
        }
        assertFailsWith<InvalidVariableException> { html(on, "highlight-languages" to text("a/b")) }
        assertFailsWith<InvalidVariableException> { html(on, "highlight-style" to text("/etc/x")) }
    }

    @Test
    fun linksComeBeforeTheStylesAndScriptsAfter() {
        val document = html(on, "css" to items("github", "my.css"))
        val head = document.substringBefore("</head>")
        val order = listOf("<title>", "styles/github.min.css", "<style>", "href=\"my.css\"", "highlight.min.js", "DOMContentLoaded").map { head.indexOf(it) }
        assertTrue(order.all { it >= 0 }, head)
        assertEquals(order.sorted(), order, head)
    }

    @Test
    fun bodyAndOtherOutputAreUnchanged() {
        val body = { document: String -> document.substringAfter("<body>").substringBefore("</body>") }
        assertEquals(body(html()), body(html(on)))
        assertEquals("<h1 id=\"heading\">Heading</h1>\n", markdownToHtml("# Heading\n", variables = mapOf(on)))
        assertEquals(html(), html("highlight-url" to text("x"), "highlight-style" to text("../invalid")))
    }

    @Test
    fun derivedStylesTakeTheBoxOfThePre() {
        for (alias in listOf("default", "intellij", "github")) {
            val head = html(on, "css" to text(alias)).substringBefore("</head>")
            val rule = head.substringAfter("pre code.hljs {").substringBefore("}")
            assertContains(rule, "background: transparent;", message = alias)
            assertContains(rule, "color: inherit;", message = alias)
            assertContains(rule, "padding: 0;", message = alias)
            assertFalse("pre:has" in head, alias)
            // Before the style of the page, so that is what overrides them; after the style of highlight.js.
            assertTrue(head.indexOf(".min.css") < head.indexOf("pre code.hljs {"), alias)
            assertTrue(head.indexOf("pre code.hljs {") < head.indexOf("--foreground"), alias)
        }
    }

    @Test
    fun requestedStylesKeepTheirOwnBox() {
        for (variable in listOf("highlight-style", "highlight-style-dark")) {
            val head = html(on, variable to text("monokai-sublime")).substringBefore("</head>")
            assertContains(head, "pre:has(> code.hljs) {\n        padding: 0;\n        background: none;\n      }")
            assertFalse("pre code.hljs" in head, variable)
            assertFalse("color: inherit" in head.substringBefore("--foreground"), variable)
        }
    }

    @Test
    fun noBoxRulesWithoutABuiltInStyle() {
        for (css in listOf(text("none"), items("none", "my.css"), text("my.css"))) {
            for (extra in listOf(emptyArray(), arrayOf("highlight-style" to text("monokai-sublime")))) {
                val head = html(on, "css" to css, *extra).substringBefore("</head>")
                assertFalse("<style>" in head, "$css $extra")
            }
        }
    }
}

class MetadataBlockTest {
    private fun text(value: String) = TemplateValue.Text(value)

    private fun extract(markdown: String) = extractMetadataBlocks(markdown)

    @Test
    fun takesBlockAtStartOfDocument() {
        val (rest, fields) = extract("---\ntitle: A\nauthor: [x, y]\n---\n\n# Heading\n")
        assertEquals("\n# Heading\n", rest)
        assertEquals(text("A"), fields["title"])
        assertEquals(TemplateValue.Items(listOf(text("x"), text("y"))), fields["author"])
    }

    @Test
    fun blockMayEndWithThreeDots() {
        val (rest, fields) = extract("---\ntitle: A\n...\ntext\n")
        assertEquals("text\n", rest)
        assertEquals(text("A"), fields["title"])
    }

    @Test
    fun delimitersMayHaveTrailingSpaces() {
        val (rest, fields) = extract("---  \ntitle: A\n---  \ntext")
        assertEquals("text", rest)
        assertEquals(text("A"), fields["title"])
    }

    @Test
    fun takesBlockAfterBlankLine() {
        val (rest, fields) = extract("text\n\n---\ntitle: A\n---\n\nmore\n")
        assertEquals("text\n\n\nmore\n", rest)
        assertEquals(text("A"), fields["title"])
    }

    @Test
    fun laterBlocksReplaceFieldsOfEarlierOnes() {
        val (rest, fields) = extract("---\ntitle: A\nkeep: yes\n---\n\ntext\n\n---\ntitle: B\n---\n")
        assertEquals("\ntext\n\n", rest)
        assertEquals(text("B"), fields["title"])
        assertEquals(text("yes"), fields["keep"])
    }

    @Test
    fun leavesThematicBreaksAlone() {
        for (markdown in listOf(
            "text\n---\ntitle: A\n---\n", // directly after text: a setext heading
            "---\n\ntitle: A\n---\n", // blank line after the opening delimiter
            " ---\ntitle: A\n---\n", // indented
            "---\ntitle: A\n", // never closed
            "---\njust text\n---\n", // not a mapping
            "---\n- a\n- b\n---\n", // not a mapping
        )) {
            assertEquals(markdown to emptyMap(), extract(markdown), markdown)
        }
    }

    @Test
    fun leavesFencedCodeAlone() {
        for (markdown in listOf(
            "```\n\n---\ntitle: A\n---\n```\n",
            "~~~yaml\n\n---\ntitle: A\n---\n~~~\n",
            "````\n```\n\n---\ntitle: A\n---\n````\n",
        )) {
            assertEquals(markdown to emptyMap(), extract(markdown), markdown)
        }
        val (rest, fields) = extract("```\ncode\n```\n\n---\ntitle: A\n---\n")
        assertEquals("```\ncode\n```\n\n", rest)
        assertEquals(text("A"), fields["title"])
    }

    @Test
    fun rejectsInvalidYaml() {
        val error = assertFailsWith<InvalidMetadataException> { extract("text\n\n---\ntitle: [a\n---\n") }
        assertContains(error.message.orEmpty(), "line 3")
    }

    @Test
    fun blocksAreRemovedFromTheOutput() {
        val html = markdownToHtml("---\ntitle: A\n---\n\n# Heading\n")
        assertEquals("<h1 id=\"heading\">Heading</h1>\n", html)
        assertEquals("", markdownToHtml("---\ntitle: A\n---\n"))
        assertFalse("<hr" in markdownToHtml("---\ntitle: A\n---\n\ntext\n", standalone = true))
    }

    @Test
    fun crlfBlocksAreRecognized() {
        assertEquals("<p>text</p>\n", markdownToHtml("---\r\ntitle: A\r\n---\r\n\r\ntext\r\n"))
    }
}

class SettingsTest {
    private fun text(value: String) = TemplateValue.Text(value)

    @Test
    fun parsesSupportedKeys() {
        val settings = parseSettings(
            """
            |standalone: true
            |flavour: commonmark
            |variables:
            |  title: V
            |  author: [a, b]
            |metadata:
            |  title: M
            |metadata-files:
            |  - one.yaml
            |  - two.yaml
            """.trimMargin()
        )
        assertEquals(true, settings.standalone)
        assertEquals(Flavour.COMMONMARK, settings.flavour)
        assertEquals(mapOf("title" to text("V"), "author" to TemplateValue.Items(listOf(text("a"), text("b")))), settings.variables)
        assertEquals(mapOf("title" to text("M")), settings.metadata)
        assertEquals(listOf("one.yaml", "two.yaml"), settings.metadataFiles)
    }

    @Test
    fun metadataFileMayBeASingleName() {
        assertEquals(listOf("one.yaml"), parseSettings("metadata-file: one.yaml").metadataFiles)
        assertEquals(listOf("one.yaml"), parseSettings("metadata-files: one.yaml").metadataFiles)
    }

    @Test
    fun emptyDefaultsSetNothing() {
        val settings = parseSettings("")
        assertEquals(null, settings.standalone)
        assertEquals(null, settings.flavour)
        assertEquals(emptyMap(), settings.variables)
        assertEquals(emptyList(), settings.metadataFiles)
    }

    @Test
    fun reportsAndSkipsUnsupportedKeys() {
        val ignored = mutableListOf<String>()
        val settings = parseSettings("from: markdown\nstandalone: true\ntemplate: x.html\n") { ignored += it }
        assertEquals(listOf("from", "template"), ignored)
        assertEquals(true, settings.standalone)
    }

    @Test
    fun rejectsValuesOfTheWrongType() {
        for (yaml in listOf(
            "standalone: maybe", "flavour: bogus", "flavour: [gfm]", "variables: x", "metadata: [a]",
            "metadata-files: {a: b}", "metadata-files: [[a]]",
        )) {
            assertFailsWith<IllegalArgumentException>(yaml) { parseSettings(yaml) }
        }
    }

    @Test
    fun laterSettingsOverrideEarlierOnes() {
        val earlier = Settings(
            standalone = true, flavour = Flavour.SPACE,
            variables = mapOf("a" to text("1"), "b" to text("1")),
            metadata = mapOf("m" to text("1")),
            metadataFiles = listOf("one.yaml"),
        )
        val later = Settings(
            flavour = Flavour.COMMONMARK,
            variables = mapOf("b" to text("2")),
            metadata = mapOf("n" to text("2")),
            metadataFiles = listOf("two.yaml"),
        )
        val merged = earlier.then(later)
        assertEquals(true, merged.standalone)
        assertEquals(Flavour.COMMONMARK, merged.flavour)
        assertEquals(mapOf("a" to text("1"), "b" to text("2")), merged.variables)
        assertEquals(mapOf("m" to text("1"), "n" to text("2")), merged.metadata)
        assertEquals(listOf("one.yaml", "two.yaml"), merged.metadataFiles)
    }
}

class MarkdownCliTest {
    private val table = "| a | b |\n|---|---|\n| 1 | 2 |\n"

    private fun run(
        vararg args: String,
        input: String = "# Title\n",
        files: Map<String, String> = emptyMap(),
    ): String {
        var output = ""
        MarkdownCli(readStdin = { input }, writeStdout = { output += it }, readFile = { files[it] }).parse(args.toList())
        return output
    }

    /** Runs the command with a file system of [files] and returns what it wrote to stdout and to files. */
    private fun runWithFiles(
        vararg args: String,
        input: String = "# Stdin\n",
        files: Map<String, String> = emptyMap(),
    ): Pair<String, Map<String, String>> {
        var stdout = ""
        val written = mutableMapOf<String, String>()
        MarkdownCli(
            readStdin = { input },
            writeStdout = { stdout += it },
            readFile = { files[it] },
            writeFile = { path, text -> if (path.startsWith("/nowhere/")) false else { written[path] = text; true } },
        ).parse(args.toList())
        return stdout to written
    }

    @Test
    fun readsInputFilesInsteadOfStdin() {
        val (stdout, written) = runWithFiles("a.md", files = mapOf("a.md" to "# A\n"))
        assertEquals("<h1 id=\"a\">A</h1>\n", stdout)
        assertEquals(emptyMap(), written)
    }

    @Test
    fun dashIsStdin() {
        val files = mapOf("a.md" to "# A\n")
        assertEquals("<h1 id=\"stdin\">Stdin</h1>\n", runWithFiles("-", files = files).first)
        val html = runWithFiles("a.md", "-", files = files).first
        assertContains(html, "<h1 id=\"a\">A</h1>")
        assertContains(html, "<h1 id=\"stdin\">Stdin</h1>")
    }

    @Test
    fun concatenatesInputFilesWithBlankLines() {
        // Without the blank line, the files would be one paragraph.
        val files = mapOf("a.md" to "first", "b.md" to "second\n", "c.md" to "third")
        assertEquals(
            "<p>first</p>\n<p>second</p>\n<p>third</p>\n",
            runWithFiles("a.md", "b.md", "c.md", files = files).first,
        )
    }

    @Test
    fun metadataFileMayBeAnInputFile() {
        val files = mapOf("book.md" to "# Hello\n", "meta.yaml" to "---\ntitle: Mine\n---\n")
        val html = runWithFiles("-s", "book.md", "meta.yaml", files = files).first
        assertContains(html, "<title>Mine</title>")
        assertFalse("<hr" in html, html)
    }

    @Test
    fun outputOptionWritesFileInsteadOfStdout() {
        for (option in listOf("-o", "--output")) {
            val (stdout, written) = runWithFiles(option, "out.html", "a.md", files = mapOf("a.md" to "# A\n"))
            assertEquals("", stdout)
            assertEquals(mapOf("out.html" to "<h1 id=\"a\">A</h1>\n"), written)
        }
        assertEquals(mapOf("out.html" to "<h1 id=\"stdin\">Stdin</h1>\n"), runWithFiles("-o", "out.html").second)
    }

    @Test
    fun outputDashIsStdout() {
        val (stdout, written) = runWithFiles("-o", "-")
        assertEquals("<h1 id=\"stdin\">Stdin</h1>\n", stdout)
        assertEquals(emptyMap(), written)
    }

    @Test
    fun rejectsMissingInputFile() {
        assertFailsWith<CliktError> { runWithFiles("missing.md") }
    }

    @Test
    fun rejectsUnwritableOutputFile() {
        assertFailsWith<CliktError> { runWithFiles("-o", "/nowhere/out.html") }
    }

    @Test
    fun writesNoOutputFileIfTheInputIsInvalid() {
        val written = mutableMapOf<String, String>()
        val cli = MarkdownCli(readStdin = { "---\ntitle: [\n---\n" }, writeFile = { path, text -> written[path] = text; true })
        assertFailsWith<CliktError> { cli.parse(listOf("-o", "out.html")) }
        assertEquals(emptyMap(), written)
    }

    @Test
    fun defaultsFileSetsInputAndOutputFiles() {
        val files = mapOf(
            "a.md" to "# A\n", "b.md" to "# B\n",
            "d.yaml" to "input-files: [a.md]\noutput-file: out.html\n",
            "empty.yaml" to "output-file:\n",
        )
        assertEquals(mapOf("out.html" to "<h1 id=\"a\">A</h1>\n"), runWithFiles("-d", "d.yaml", files = files).second)
        // The command line replaces the input files and overrides the output file.
        val (stdout, written) = runWithFiles("-d", "d.yaml", "-d", "empty.yaml", "b.md", files = files)
        assertEquals("<h1 id=\"b\">B</h1>\n", stdout)
        assertEquals(emptyMap(), written)
    }

    @Test
    fun writesHtmlForInput() {
        assertEquals("<h1 id=\"title\">Title</h1>\n", run())
    }

    @Test
    fun standaloneOptionWritesCompleteDocument() {
        for (option in listOf("-s", "--standalone")) {
            val html = run(option)
            assertTrue(html.startsWith("<!DOCTYPE html>\n<html>\n"), html)
            assertContains(html, "<title>Title</title>")
            assertContains(html, "<h1 id=\"title\">Title</h1>")
        }
        assertEquals(run("-s"), markdownToHtml("# Title\n", standalone = true))
    }

    @Test
    fun defaultsToGfm() {
        val table = "| a | b |\n|---|---|\n| 1 | 2 |\n"
        assertContains(run(input = table), "<table>")
        assertEquals(run("--flavour", "gfm", input = table), run(input = table))
    }

    @Test
    fun flavourOptionSelectsFlavour() {
        val table = "| a | b |\n|---|---|\n| 1 | 2 |\n"
        assertFalse("<table>" in run("--flavour", "commonmark", input = table))
        assertContains(run("-f", "space", input = table), "<table>")
    }

    @Test
    fun rejectsUnknownFlavour() {
        assertFailsWith<BadParameterValue> { run("--flavour", "bogus") }
    }

    @Test
    fun acceptsVariables() {
        val plain = run()
        for (option in listOf("-V", "--variable")) {
            assertEquals(plain, run(option, "title=Hello", option, "draft", option, "title=Again"))
        }
    }

    @Test
    fun rejectsVariableWithoutName() {
        assertFailsWith<BadParameterValue> { run("-V", "=Hello") }
    }

    @Test
    fun readsMetadataFiles() {
        val files = mapOf("a.yaml" to "title: A\n", "b.yaml" to "")
        assertEquals(run(), run("--metadata-file", "a.yaml", "--metadata-file", "b.yaml", files = files))
    }

    @Test
    fun rejectsMissingMetadataFile() {
        val error = assertFailsWith<CliktError> { run("--metadata-file", "nope.yaml") }
        assertContains(error.message.orEmpty(), "nope.yaml")
    }

    @Test
    fun rejectsInvalidMetadataFile() {
        val files = mapOf("bad.yaml" to "- a\n", "broken.yaml" to "a: [b")
        for (name in files.keys) {
            val error = assertFailsWith<CliktError> { run("--metadata-file", name, files = files) }
            assertContains(error.message.orEmpty(), name)
        }
    }

    @Test
    fun defaultsFileSetsOptions() {
        val files = mapOf("d.yaml" to "standalone: true\nflavour: commonmark\n")
        for (option in listOf("-d", "--defaults")) {
            val html = run(option, "d.yaml", input = table, files = files)
            assertTrue(html.startsWith("<!DOCTYPE html>"), html)
            assertFalse("<table>" in html, html)
        }
    }

    @Test
    fun defaultsFileNeedsNoYamlExtension() {
        assertEquals(run("-s"), run("-d", "d", files = mapOf("d.yaml" to "standalone: true\n")))
    }

    @Test
    fun commandLineOverridesDefaultsFiles() {
        val files = mapOf("one.yaml" to "flavour: commonmark\n", "two.yaml" to "flavour: space\n")
        assertFalse("<table>" in run("-d", "one.yaml", input = table, files = files))
        assertContains(run("-d", "one.yaml", "-d", "two.yaml", input = table, files = files), "<table>")
        assertContains(run("-d", "one.yaml", "-f", "gfm", input = table, files = files), "<table>")
    }

    @Test
    fun defaultsFileMetadataFilesAreRead() {
        val files = mapOf("d.yaml" to "metadata-files: [a.yaml]\n", "a.yaml" to "title: A\n")
        assertEquals(run(), run("-d", "d.yaml", files = files))
        assertFailsWith<CliktError> { run("-d", "d.yaml", files = files - "a.yaml") }
    }

    @Test
    fun rejectsMissingOrInvalidDefaultsFile() {
        val missing = assertFailsWith<CliktError> { run("-d", "nope.yaml") }
        assertContains(missing.message.orEmpty(), "nope.yaml")
        val invalid = assertFailsWith<CliktError> { run("-d", "bad.yaml", files = mapOf("bad.yaml" to "flavour: bogus")) }
        assertContains(invalid.message.orEmpty(), "bad.yaml")
        assertContains(invalid.message.orEmpty(), "bogus")
    }

    @Test
    fun everyMethodSetsTheTitle() {
        val files = mapOf(
            "meta.yaml" to "title: From a metadata file\n",
            "defaults-variables.yaml" to "variables:\n  title: From the variables of a defaults file\n",
            "defaults-metadata.yaml" to "metadata:\n  title: From the metadata of a defaults file\n",
            "defaults-files.yaml" to "metadata-files: [meta.yaml]\n",
        )
        fun title(vararg args: String, input: String = "# Heading\n") =
            run("-s", *args, input = input, files = files).substringAfter("<title>").substringBefore("</title>")
        assertEquals("Heading", title())
        assertEquals("From the command line", title("-V", "title=From the command line"))
        assertEquals("From a metadata file", title("--metadata-file", "meta.yaml"))
        assertEquals("From the variables of a defaults file", title("-d", "defaults-variables"))
        assertEquals("From the metadata of a defaults file", title("-d", "defaults-metadata"))
        assertEquals("From a metadata file", title("-d", "defaults-files"))
        assertEquals("From the document", title(input = "---\ntitle: From the document\n---\n"))
    }

    @Test
    fun titlePrecedenceLikePandoc() {
        val files = mapOf(
            "meta.yaml" to "title: file\n",
            "defaults.yaml" to "metadata:\n  title: defaults metadata\nvariables:\n  title: defaults variable\n",
            "defaults-metadata.yaml" to "metadata:\n  title: defaults metadata\n",
        )
        fun title(vararg args: String) = run("-s", *args, input = "---\ntitle: document\n---\n", files = files)
            .substringAfter("<title>").substringBefore("</title>")
        assertEquals("document", title("--metadata-file", "meta.yaml"))
        assertEquals("defaults metadata", title("--metadata-file", "meta.yaml", "-d", "defaults-metadata"))
        assertEquals("defaults variable", title("-d", "defaults"))
        assertEquals("command line", title("-d", "defaults", "-V", "title=command line"))
    }

    @Test
    fun rejectsInvalidMetadataBlockInInput() {
        val error = assertFailsWith<CliktError> { run(input = "---\ntitle: [a\n---\n") }
        assertContains(error.message.orEmpty(), "line 1")
    }

    @Test
    fun everyMethodSetsTheStyles() {
        val files = mapOf("meta.yaml" to "css: [meta.css]\n", "defaults.yaml" to "standalone: true\nvariables:\n  css: [github, extra.css]\n")
        fun styles(vararg args: String, input: String = "# Heading\n") =
            run("-s", *args, input = input, files = files).lines().filter { "<link" in it || "<style" in it }.map { it.trim() }

        assertEquals(listOf("<style>"), styles())
        assertEquals(emptyList(), styles("-V", "css=none"))
        assertEquals(listOf("<link rel=\"stylesheet\" href=\"a.css\">", "<link rel=\"stylesheet\" href=\"b.css\">"), styles("-V", "css=a.css", "-V", "css=b.css"))
        assertEquals(listOf("<link rel=\"stylesheet\" href=\"meta.css\">"), styles("--metadata-file", "meta.yaml"))
        assertEquals(listOf("<link rel=\"stylesheet\" href=\"doc.css\">"), styles(input = "---\ncss: doc.css\n---\n"))
        assertEquals(listOf("<style>", "<link rel=\"stylesheet\" href=\"extra.css\">"), styles("-d", "defaults.yaml"))
        assertEquals(emptyList(), styles("-d", "defaults.yaml", "-V", "css=none"))
    }

    @Test
    fun highlightWorksFromEveryMethod() {
        val files = mapOf("meta.yaml" to "highlight: true\nhighlight-style: a11y-dark\n", "d.yaml" to "variables:\n  highlight: true\n  highlight-url: ./hl\n")
        fun scripts(vararg args: String, input: String = "# T\n") = run("-s", *args, input = input, files = files).lines().filter { "<script src" in it }.map { it.trim() }

        assertEquals(emptyList(), scripts())
        assertEquals(listOf("<script src=\"$DEFAULT_HIGHLIGHT_URL/highlight.min.js\" defer></script>"), scripts("-V", "highlight"))
        assertEquals(listOf("<script src=\"$DEFAULT_HIGHLIGHT_URL/highlight.min.js\" defer></script>"), scripts("-V", "highlight=true"))
        assertEquals(listOf("<script src=\"./hl/highlight.min.js\" defer></script>"), scripts("-d", "d.yaml"))
        assertEquals(emptyList(), scripts("-d", "d.yaml", "-V", "highlight=false"))
        assertEquals(1, scripts("--metadata-file", "meta.yaml").size)
        assertEquals(1, scripts(input = "---\nhighlight: true\n---\n").size)
        assertContains(run("-s", "--metadata-file", "meta.yaml", files = files), "styles/a11y-dark.min.css")
    }

    @Test
    fun rejectsInvalidHighlightVariables() {
        val error = assertFailsWith<CliktError> { run("-s", "-V", "highlight=ture") }
        assertContains(error.message.orEmpty(), "`highlight`")
        assertFailsWith<CliktError> { run("-s", "-V", "highlight", "-V", "highlight-style=../x") }
    }

    @Test
    fun rejectsUnknownOptions() {
        assertFailsWith<UsageError> { run("--does-not-exist") }
    }
}
