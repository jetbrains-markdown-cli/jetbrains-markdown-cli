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
        MarkdownCli(readInput = { input }, write = { output += it }, readFile = { files[it] }).parse(args.toList())
        return output
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
    fun rejectsUnknownOptions() {
        assertFailsWith<UsageError> { run("--does-not-exist") }
    }
}
