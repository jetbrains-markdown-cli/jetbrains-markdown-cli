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
        val html = markdownToHtml("- a\n- b\n\n| a |\n|---|\n| 1 |\n")
        assertContains(html, "<body>\n  <ul>\n    <li>a</li>\n    <li>b</li>\n  </ul>\n")
        assertContains(html, "  <table>\n    <thead>\n      <tr>\n        <th>a</th>\n")
        assertTrue(html.endsWith("</body>\n"), html)
    }

    @Test
    fun keepsCodeBlockContentUntouched() {
        val html = markdownToHtml("```sh\nfoo\n  bar\n```\n")
        assertContains(html, "  <pre><code class=\"language-sh\">foo\n  bar\n</code></pre>\n")
        assertFalse(html.contains("</code>\n"), html)
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
        assertContains(run(), "<h1 id=\"title\">Title</h1>")
    }

    @Test
    fun rejectsUnknownOptions() {
        assertFailsWith<UsageError> { run("--does-not-exist") }
    }
}
