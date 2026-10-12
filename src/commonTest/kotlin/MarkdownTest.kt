import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class MarkdownTest {
    @Test
    fun rendersHeadingAndEmphasis() {
        val html = markdownToHtml("# Title\n\nsome *emphasis*\n")
        assertContains(html, "<h1>Title</h1>")
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
}
