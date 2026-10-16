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

class MarkdownCliTest {
    private fun run(vararg args: String, input: String = "# Title\n"): String {
        var output = ""
        MarkdownCli(readInput = { input }, write = { output += it }).parse(args.toList())
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
    fun rejectsUnknownOptions() {
        assertFailsWith<UsageError> { run("--does-not-exist") }
    }
}
