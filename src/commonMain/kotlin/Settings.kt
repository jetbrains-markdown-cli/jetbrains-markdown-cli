/**
 * The options a defaults file (`-d`/`--defaults`) can set. Command line options are expressed the same way,
 * so that defaults files and the command line are simply layered with [then].
 *
 * `null` and empty values mean "not set".
 */
internal class Settings(
    val standalone: Boolean? = null,
    val flavour: Flavour? = null,
    /** `--toc`: the table of contents is generated. */
    val tableOfContents: Boolean? = null,
    val tocDepth: Int? = null,
    val variables: TemplateContext = emptyMap(),
    /** Metadata that the document's own metadata cannot override, like pandoc's `metadata:` defaults and `-M`. */
    val metadata: TemplateContext = emptyMap(),
    val metadataFiles: List<String> = emptyList(),
    /** The Markdown files to read, `-` being stdin. Empty means stdin as well. */
    val inputFiles: List<String> = emptyList(),
    /** The file to write, `-` being stdout. */
    val outputFile: String? = null,
) {
    /** These settings, overridden by [later]: single values and top-level fields are replaced, metadata files are appended, input files are replaced. */
    fun then(later: Settings) = Settings(
        standalone = later.standalone ?: standalone,
        flavour = later.flavour ?: flavour,
        tableOfContents = later.tableOfContents ?: tableOfContents,
        tocDepth = later.tocDepth ?: tocDepth,
        variables = variables + later.variables,
        metadata = metadata + later.metadata,
        metadataFiles = metadataFiles + later.metadataFiles,
        inputFiles = later.inputFiles.ifEmpty { inputFiles },
        outputFile = later.outputFile ?: outputFile,
    )
}

/**
 * Parses the YAML of a defaults file, which uses the long option names like pandoc's. Keys that are not
 * supported (pandoc has many more options) are reported to [ignored] and otherwise skipped.
 *
 * @throws IllegalArgumentException if the YAML is invalid or a value has the wrong type
 */
internal fun parseSettings(yaml: String, ignored: (key: String) -> Unit = {}): Settings =
    parseYamlFields(yaml).entries.fold(Settings()) { settings, (key, value) ->
        settings.then(
            when (key) {
                "standalone" -> Settings(standalone = (value as? TemplateValue.Flag)?.value ?: invalid(key, "a boolean"))
                "flavour" -> Settings(flavour = (value as? TemplateValue.Text)?.let(::flavourNamed) ?: invalid(key, "a text"))
                "toc", "table-of-contents" -> Settings(tableOfContents = (value as? TemplateValue.Flag)?.value ?: invalid(key, "a boolean"))
                "toc-depth" -> Settings(tocDepth = value.asTocDepth(key))
                "variables" -> Settings(variables = (value as? TemplateValue.Fields)?.values ?: invalid(key, "a mapping"))
                "metadata" -> Settings(metadata = (value as? TemplateValue.Fields)?.values ?: invalid(key, "a mapping"))
                "metadata-file", "metadata-files" -> Settings(metadataFiles = value.asFileList(key))
                "input-file", "input-files" -> Settings(inputFiles = value.asFileList(key))
                // Like in pandoc, an empty `output-file` means stdout.
                "output-file" -> Settings(outputFile = (value as? TemplateValue.Text)?.value?.ifEmpty { STDIO } ?: invalid(key, "a file name"))
                else -> Settings().also { ignored(key) }
            }
        )
    }

/** The file name that stands for stdin as an input and stdout as an output, like in pandoc's `-o -`. */
internal const val STDIO = "-"

private fun invalid(key: String, expected: String): Nothing =
    throw IllegalArgumentException("`$key` must be $expected")

private fun flavourNamed(name: TemplateValue.Text): Flavour =
    Flavour.entries.firstOrNull { it.cliName == name.value }
        ?: throw IllegalArgumentException(
            "unknown flavour `${name.value}`, expected one of ${Flavour.entries.joinToString { it.cliName }}"
        )

/** A single file name or a list of them. */
private fun TemplateValue.asFileList(key: String): List<String> = when (this) {
    is TemplateValue.Text -> listOf(value)
    is TemplateValue.Items -> values.map { (it as? TemplateValue.Text)?.value ?: invalid(key, "a file name or a list of them") }
    else -> invalid(key, "a file name or a list of them")
}

private fun TemplateValue.asTocDepth(key: String): Int =
    (this as? TemplateValue.Text)?.value?.toIntOrNull()?.takeIf { it in 1..MAX_TOC_DEPTH }
        ?: invalid(key, "a number from 1 to $MAX_TOC_DEPTH")
