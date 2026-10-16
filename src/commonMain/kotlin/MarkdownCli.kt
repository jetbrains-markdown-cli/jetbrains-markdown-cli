import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context

/**
 * Command line interface: reads Markdown via [readInput] and passes the HTML document to [write].
 *
 * I/O is injected so that the command stays in `commonMain` and can be tested without touching stdin/stdout.
 */
class MarkdownCli(
    private val readInput: () -> String,
    private val write: (String) -> Unit = { print(it) },
) : CliktCommand(name = "jetbrains-markdown-cli") {
    override fun help(context: Context) = "Convert Markdown from stdin to an HTML document on stdout."

    override fun run() {
        write(markdownToHtml(readInput()))
    }
}
