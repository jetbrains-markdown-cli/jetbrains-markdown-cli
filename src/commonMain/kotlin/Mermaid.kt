/** Where Mermaid is loaded from unless `mermaid-url` says otherwise: the latest release of the official package. */
internal const val DEFAULT_MERMAID_URL = "https://cdn.jsdelivr.net/npm/mermaid/dist"

/** The themes Mermaid has built in, which is what `mermaid-theme` and `mermaid-theme-dark` can be. */
private val MERMAID_THEMES = listOf("default", "neutral", "dark", "forest", "base")

/** The rendered diagram is a `<div>`, not a `<pre>`: it takes none of the box, monospace font or colours of code. */
private val MERMAID_CSS = listOf(
    "div.mermaid {",
    "  margin: 1em 0;",
    "  text-align: center;",
    "  overflow-x: auto;",
    "}",
    "",
    "@media print {",
    "  div.mermaid {",
    "    break-inside: avoid;",
    "  }",
    "}",
)

/**
 * Renders the code fences of the language `mermaid` in the browser, in place of the code block: `<code class="language-mermaid">`
 * is what it looks for. A diagram Mermaid cannot parse stays a code block, without the error drawing Mermaid would make.
 * Runs after the deferred script. The diagrams are drawn one after the other, because the theme is a setting of Mermaid
 * as a whole, and again when the browser switches between light and dark, since Mermaid puts the theme into the SVG.
 */
private fun mermaidScript(light: String, dark: String?) = """document.addEventListener("DOMContentLoaded", function () {
  if (typeof mermaid === "undefined") return;
  var light = "$light";
  var dark = ${if (dark == null) "null" else "\"$dark\""};
  var query = window.matchMedia("(prefers-color-scheme: dark)");
  var diagrams = [];
  var queue = Promise.resolve();
  var count = 0;
  function enqueue(task) {
    queue = queue.then(task).catch(function (error) { console.error(error); });
  }
  function draw(diagram) {
    var id = "mermaid-diagram-" + count++;
    mermaid.initialize({ startOnLoad: false, theme: dark !== null && query.matches ? dark : light });
    return mermaid.render(id, diagram.source).then(function (result) {
      diagram.element.innerHTML = result.svg;
    }, function (error) {
      var leftover = document.getElementById("d" + id);
      if (leftover) leftover.remove();
      throw error;
    });
  }
  document.querySelectorAll('pre > code[class*="language-"]').forEach(function (code) {
    if (!/(?:^|\s)language-mermaid(?:\s|$)/.test(code.className)) return;
    var pre = code.parentNode;
    var diagram = { source: code.textContent, element: document.createElement("div") };
    diagram.element.className = "mermaid";
    enqueue(function () {
      return mermaid.parse(diagram.source, { suppressErrors: true }).then(function (valid) {
        if (!valid) return;
        return draw(diagram).then(function () {
          pre.replaceWith(diagram.element);
          diagrams.push(diagram);
        });
      });
    });
  });
  if (dark !== null) {
    query.addEventListener("change", function () {
      diagrams.forEach(function (diagram) { enqueue(function () { return draw(diagram); }); });
    });
  }
});"""

/** What `mermaid` and its companion variables ask for, see [mermaidScriptTags]. */
private class Mermaiding(val script: String, val light: String, val dark: String?)

/**
 * The `<style>` for the diagrams of the variable `mermaid` (`true`; nothing is rendered otherwise). It comes before the
 * styles of [styleTags], which can therefore override it.
 *
 * The variables:
 * - `mermaid-url`: where Mermaid comes from, by default [DEFAULT_MERMAID_URL]. Either the directory that has `mermaid.min.js`
 *   (like the official `mermaid` package, cdnjs and unpkg), or the URL of the script itself, whatever it is called. It
 *   has to be the classic script, not the ES module.
 * - `mermaid-theme`: one of the themes of Mermaid (`default`, `neutral`, `dark`, `forest`, `base`). By default `default`.
 * - `mermaid-theme-dark`: the same for `prefers-color-scheme: dark`. By default `dark` for the built-in styles of `css`
 *   that have a dark scheme (all but `pandoc`) as long as `mermaid-theme` is not set (then it is used for both), and
 *   none otherwise because that page is not dark.
 *
 * @throws InvalidVariableException for a value of `mermaid` that is not a boolean or a theme Mermaid does not have
 */
internal fun mermaidStyleTags(context: TemplateContext): List<String> {
    mermaiding(context) ?: return emptyList()
    return listOf("    <style>") + MERMAID_CSS.map { if (it.isEmpty()) it else "      $it" } + "    </style>"
}

/** The `<script>`s that load and run Mermaid, if `mermaid` is set, see [mermaidStyleTags]. */
internal fun mermaidScriptTags(context: TemplateContext): List<String> {
    val mermaiding = mermaiding(context) ?: return emptyList()
    return listOf("""    <script src="${mermaiding.script.escapeHtml()}" defer></script>""", "    <script>") +
        mermaidScript(mermaiding.light, mermaiding.dark).lines().map { "      $it" } + "    </script>"
}

private fun mermaiding(context: TemplateContext): Mermaiding? {
    if (context["mermaid"]?.asBoolean("mermaid") != true) return null
    val location = scriptLocation(context["mermaid-url"]?.asText(), DEFAULT_MERMAID_URL, "mermaid.min.js")
    val explicitLight = themeName(context, "mermaid-theme")
    val explicitDark = themeName(context, "mermaid-theme-dark")
    val builtIn = styleEntries(context).lastOrNull { it in BuiltInStyles.css }
    return Mermaiding(
        script = location.script,
        light = explicitLight ?: "default",
        dark = explicitDark ?: if (explicitLight == null && builtIn in BuiltInStyles.darkAware) "dark" else null,
    )
}

private fun themeName(context: TemplateContext, variable: String): String? {
    val name = context[variable]?.asText()?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if (name !in MERMAID_THEMES) {
        throw InvalidVariableException("the variable `$variable` must be one of ${MERMAID_THEMES.joinToString(", ")}, not `$name`")
    }
    return name
}
