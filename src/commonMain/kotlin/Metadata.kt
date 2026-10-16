import it.krzeminski.snakeyaml.engine.kmp.api.LoadSettings
import it.krzeminski.snakeyaml.engine.kmp.api.lowlevel.Compose
import it.krzeminski.snakeyaml.engine.kmp.exceptions.YamlEngineException
import it.krzeminski.snakeyaml.engine.kmp.nodes.MappingNode
import it.krzeminski.snakeyaml.engine.kmp.nodes.Node
import it.krzeminski.snakeyaml.engine.kmp.nodes.ScalarNode
import it.krzeminski.snakeyaml.engine.kmp.nodes.SequenceNode
import it.krzeminski.snakeyaml.engine.kmp.nodes.Tag

private const val MAX_DEPTH = 64

/**
 * Parses [yaml], which must be a mapping (or empty), into template values the way pandoc reads metadata:
 * booleans are booleans, null is the empty text, and every other scalar (numbers included) is kept as written.
 *
 * @throws IllegalArgumentException if [yaml] is not valid YAML or not a mapping
 */
internal fun parseYamlFields(yaml: String): Map<String, TemplateValue> {
    val root = try {
        Compose(LoadSettings()).compose(yaml.removePrefix("﻿"))
    } catch (e: YamlEngineException) {
        throw IllegalArgumentException("invalid YAML: ${e.message}", e)
    } ?: return emptyMap()
    return (root.toTemplateValue(depth = 0) as? TemplateValue.Fields)?.values
        ?: throw IllegalArgumentException("expected a YAML mapping")
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
