import com.github.ajalt.clikt.core.parse
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
    fun rejectsUnknownOptions() {
        assertFailsWith<UsageError> { run("--does-not-exist") }
    }
}
