import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.enum

/**
 * Command line interface: reads Markdown via [readInput] and passes the HTML to [write].
 *
 * I/O is injected (a [readFile] that returns null for a file it cannot read) so that the command stays in `commonMain` and can be tested without touching stdin/stdout.
 */
class MarkdownCli(
    private val readInput: () -> String,
    private val write: (String) -> Unit = { print(it) },
    private val readFile: (String) -> String? = { null },
) : CliktCommand(name = "jetbrains-markdown-cli") {
    override fun help(context: Context) = "Convert Markdown from stdin to HTML on stdout."

    private val standalone by option("-s", "--standalone", help = "Produce a complete HTML document instead of just its content.")
        .flag()

    private val flavour by option("-f", "--flavour", help = "Markdown flavour to parse the input with (default: ${Flavour.DEFAULT.cliName}).")
        .enum<Flavour> { it.cliName }
        .default(Flavour.DEFAULT)

    private val variables by option("-V", "--variable", metavar = "KEY[=VAL]", help = "Set a template variable; without a value it is true. Can be repeated.")
        .convert { parseVariable(it) }
        .multiple()

    private val metadataFiles by option("--metadata-file", metavar = "FILE", help = "Read metadata from a YAML file. Can be repeated, later files override earlier ones.")
        .multiple()

    override fun run() {
        val metadata = mergeMetadata(metadataFiles.map { path ->
            val text = readFile(path) ?: throw CliktError("Could not read metadata file $path")
            try {
                parseYamlFields(text)
            } catch (e: IllegalArgumentException) {
                throw CliktError("Error in metadata file $path: ${e.message}")
            }
        })
        write(markdownToHtml(readInput(), flavour, standalone, variablesOf(variables), metadata))
    }
}
