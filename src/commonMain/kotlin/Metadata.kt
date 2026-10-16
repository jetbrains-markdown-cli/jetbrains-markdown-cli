import it.krzeminski.snakeyaml.engine.kmp.api.LoadSettings
import it.krzeminski.snakeyaml.engine.kmp.api.lowlevel.Compose
import it.krzeminski.snakeyaml.engine.kmp.exceptions.YamlEngineException
import it.krzeminski.snakeyaml.engine.kmp.nodes.MappingNode
import it.krzeminski.snakeyaml.engine.kmp.nodes.Node
import it.krzeminski.snakeyaml.engine.kmp.nodes.ScalarNode
import it.krzeminski.snakeyaml.engine.kmp.nodes.SequenceNode
import it.krzeminski.snakeyaml.engine.kmp.nodes.Tag

private const val MAX_DEPTH = 64

/** The document contains a YAML metadata block that is not valid YAML. */
class InvalidMetadataException(message: String) : IllegalArgumentException(message)

/**
 * Parses [yaml], which must be a mapping (or empty), into template values the way pandoc reads metadata:
 * booleans are booleans, null is the empty text, and every other scalar (numbers included) is kept as written.
 *
 * @throws IllegalArgumentException if [yaml] is not valid YAML or not a mapping
 */
internal fun parseYamlFields(yaml: String): Map<String, TemplateValue> =
    parseYamlMapping(yaml) ?: throw IllegalArgumentException("expected a YAML mapping")

/** Like [parseYamlFields], but returns null instead of failing if [yaml] is valid YAML but not a mapping. */
internal fun parseYamlMapping(yaml: String): Map<String, TemplateValue>? {
    val root = try {
        Compose(LoadSettings()).compose(yaml.removePrefix("\uFEFF"))
    } catch (e: YamlEngineException) {
        throw IllegalArgumentException("invalid YAML: ${e.message}", e)
    } ?: return emptyMap()
    return (root.toTemplateValue(depth = 0) as? TemplateValue.Fields)?.values
}

private fun Node.toTemplateValue(depth: Int): TemplateValue {
    require(depth <= MAX_DEPTH) { "YAML nested too deeply" }
    return when (this) {
        is ScalarNode -> when (tag) {
            Tag.BOOL -> TemplateValue.Flag(value.lowercase() == "true")
            Tag.NULL -> TemplateValue.Text("")
            else -> TemplateValue.Text(value)
        }
        is SequenceNode -> TemplateValue.Items(value.map { it.toTemplateValue(depth + 1) })
        is MappingNode -> TemplateValue.Fields(
            value.associate { entry ->
                val key = entry.keyNode
                require(key is ScalarNode) { "mapping keys must be scalars" }
                key.value to entry.valueNode.toTemplateValue(depth + 1)
            }
        )
        else -> throw IllegalArgumentException("unsupported YAML node")
    }
}

/**
 * Merges metadata, lowest precedence first. Like in pandoc only top-level fields are merged:
 * a field of a later layer replaces the same field of an earlier one entirely.
 */
internal fun mergeMetadata(layers: List<Map<String, TemplateValue>>): TemplateContext =
    layers.fold(LinkedHashMap<String, TemplateValue>()) { merged, layer -> merged.apply { putAll(layer) } }

/**
 * Takes the YAML metadata blocks out of [markdown] (which must use LF line endings) like pandoc does: a block starts
 * with a `---` line at the start of the document or after a blank line, is not followed by a blank line and ends
 * with a `---` or `...` line. Blocks inside fenced code and blocks that are not YAML mappings are left alone.
 * The fields of later blocks replace those of earlier ones.
 *
 * @return the remaining Markdown and the metadata fields
 * @throws InvalidMetadataException if a block is not valid YAML
 */
internal fun extractMetadataBlocks(markdown: String): Pair<String, Map<String, TemplateValue>> {
    val lines = markdown.split('\n')
    val kept = ArrayList<String>(lines.size)
    val layers = ArrayList<Map<String, TemplateValue>>()
    var fence: Pair<Char, Int>? = null
    var previousBlank = true
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val openFence = fence
        val newFence = if (openFence == null) opensFence(line) else null
        if (openFence != null) {
            if (closesFence(line, openFence)) fence = null
        } else if (newFence != null) {
            fence = newFence
        } else if (previousBlank && isMetadataDelimiter(line, "---") && i + 1 < lines.size && lines[i + 1].isNotBlank()) {
            val end = (i + 2 until lines.size).firstOrNull { isMetadataDelimiter(lines[it], "---") || isMetadataDelimiter(lines[it], "...") }
            val fields = end?.let { parseBlock(lines.subList(i + 1, it), line = i + 1) }
            if (end != null && fields != null) {
                layers += fields
                i = end + 1
                previousBlank = true
                continue
            }
        }
        kept += line
        previousBlank = line.isBlank()
        i++
    }
    return kept.joinToString("\n") to mergeMetadata(layers)
}

private fun parseBlock(yamlLines: List<String>, line: Int): Map<String, TemplateValue>? = try {
    parseYamlMapping(yamlLines.joinToString("\n"))
} catch (e: IllegalArgumentException) {
    throw InvalidMetadataException("YAML metadata block at line $line: ${e.message}")
}

private fun isMetadataDelimiter(line: String, delimiter: String) = line.trimEnd(' ', '\t') == delimiter

/** The fence character and length if [line] opens a fenced code block. */
private fun opensFence(line: String): Pair<Char, Int>? {
    val text = line.trimStart(' ')
    if (line.length - text.length > 3) return null
    val char = text.firstOrNull()?.takeIf { it == '`' || it == '~' } ?: return null
    val length = text.takeWhile { it == char }.length
    if (length < 3 || (char == '`' && '`' in text.substring(length))) return null
    return char to length
}

private fun closesFence(line: String, fence: Pair<Char, Int>): Boolean {
    val text = line.trimStart(' ')
    if (line.length - text.length > 3) return false
    val length = text.takeWhile { it == fence.first }.length
    return length >= fence.second && text.substring(length).isBlank()
}
