import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.restrictTo

/**
 * Command line interface: reads the Markdown input files (or stdin) and writes the HTML to the output file (or stdout),
 * like pandoc: no input files means stdin, no `-o` means stdout, and the file name `-` stands for stdin or stdout.
 *
 * I/O is injected (a [readFile] that returns null for a file it cannot read, a [writeFile] that returns false for a
 * file it cannot write) so that the command stays in `commonMain` and can be tested without touching
 * stdin/stdout or the file system.
 */
class MarkdownCli(
    private val readStdin: () -> String,
    private val writeStdout: (String) -> Unit = { print(it) },
    private val readFile: (String) -> String? = { null },
    private val writeFile: (path: String, text: String) -> Boolean = { _, _ -> false },
) : CliktCommand(name = "jetbrains-markdown-cli") {
    override fun help(context: Context) =
        "Convert Markdown files to HTML. Without input files, or for the file name `-`, Markdown is read from stdin; " +
            "without `-o`, or for `-o -`, HTML is written to stdout."

    private val inputFiles by argument("INPUT", help = "Markdown files to convert, concatenated with blank lines between them. `-` is stdin.")
        .multiple()

    private val outputFile by option("-o", "--output", metavar = "FILE", help = "Write the HTML to FILE instead of stdout. `-` is stdout.")

    private val standalone by option("-s", "--standalone", help = "Produce a complete HTML document instead of just its content.")
        .flag()

    private val flavour by option("-f", "--flavour", help = "Markdown flavour to parse the input with (default: ${Flavour.DEFAULT.cliName}).")
        .enum<Flavour> { it.cliName }

    private val tableOfContents by option("--toc", "--table-of-contents", help = "Include an automatically generated table of contents (with --standalone).")
        .flag()

    private val tocDepth by option("--toc-depth", metavar = "NUMBER", help = "Heading levels the table of contents covers (default: $DEFAULT_TOC_DEPTH).")
        .int().restrictTo(1..MAX_TOC_DEPTH)

    private val variables by option("-V", "--variable", metavar = "KEY[=VAL]", help = "Set a template variable; without a value it is true. Can be repeated.")
        .convert { parseVariable(it) }
        .multiple()

    private val metadataFiles by option("--metadata-file", metavar = "FILE", help = "Read metadata from a YAML file. Can be repeated, later files override earlier ones.")
        .multiple()

    private val defaultsFiles by option("-d", "--defaults", metavar = "FILE", help = "Read options from a YAML defaults file, like pandoc. Can be repeated, later files override earlier ones; the command line overrides them all.")
        .multiple()

    override fun run() {
        val commandLine = Settings(
            standalone = if (standalone) true else null,
            flavour = flavour,
            tableOfContents = if (tableOfContents) true else null,
            tocDepth = tocDepth,
            variables = variablesOf(variables),
            metadataFiles = metadataFiles,
            inputFiles = inputFiles,
            outputFile = outputFile,
        )
        val settings = defaultsFiles.map(::loadDefaults).fold(Settings(), Settings::then).then(commandLine)
        val metadata = mergeMetadata(settings.metadataFiles.map(::loadMetadataFile))
        val html = try {
            markdownToHtml(
                readInputs(settings.inputFiles),
                settings.flavour ?: Flavour.DEFAULT,
                settings.standalone ?: false,
                settings.variables,
                metadata,
                settings.metadata,
                settings.tableOfContents ?: false,
                settings.tocDepth ?: DEFAULT_TOC_DEPTH,
            )
        } catch (e: InvalidMetadataException) {
            throw CliktError("Error in the input: ${e.message}")
        } catch (e: InvalidVariableException) {
            throw CliktError("Error in the variables: ${e.message}")
        }
        val output = settings.outputFile ?: STDIO
        if (output == STDIO) writeStdout(html)
        else if (!writeFile(output, html)) throw CliktError("Could not write output file $output")
    }

    /** The text of [files] (stdin if there are none), joined like pandoc does: with a blank line between them. */
    private fun readInputs(files: List<String>): String =
        files.ifEmpty { listOf(STDIO) }.joinToString("\n") { path ->
            val text = if (path == STDIO) readStdin() else readFile(path) ?: throw CliktError("Could not read input file $path")
            if (text.isEmpty() || text.endsWith("\n")) text else text + "\n"
        }

    private fun loadDefaults(path: String): Settings {
        // Like pandoc, `-d name` also finds `name.yaml`.
        val text = readFile(path) ?: (if (path.endsWith(".yaml")) null else readFile("$path.yaml"))
            ?: throw CliktError("Could not read defaults file $path")
        return try {
            parseSettings(text) { key -> echo("Warning: ignoring unsupported option `$key` in defaults file $path", err = true) }
        } catch (e: IllegalArgumentException) {
            throw CliktError("Error in defaults file $path: ${e.message}")
        }
    }

    private fun loadMetadataFile(path: String): Map<String, TemplateValue> {
        val text = readFile(path) ?: throw CliktError("Could not read metadata file $path")
        return try {
            parseYamlFields(text)
        } catch (e: IllegalArgumentException) {
            throw CliktError("Error in metadata file $path: ${e.message}")
        }
    }
}
