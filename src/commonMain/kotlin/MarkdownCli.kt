import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option

/**
 * Command line interface: reads Markdown via [readInput] and passes the HTML to [write].
 *
 * I/O is injected so that the command stays in `commonMain` and can be tested without touching stdin/stdout.
 */
class MarkdownCli(
    private val readInput: () -> String,
    private val write: (String) -> Unit = { print(it) },
) : CliktCommand(name = "jetbrains-markdown-cli") {
    override fun help(context: Context) = "Convert Markdown from stdin to HTML on stdout."

    private val standalone by option("-s", "--standalone", help = "Produce a complete HTML document instead of just its content.")
        .flag()

    override fun run() {
        write(markdownToHtml(readInput(), standalone))
    }
}
