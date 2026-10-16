/** Where the script of a client-side library is, and the directory its other files (styles, languages) are in. */
internal class ScriptLocation(val script: String, private val dir: String?) {
    /** [path] in the directory of the script, or next to the page if the script is. */
    fun inDir(path: String) = if (dir == null) path else "$dir/$path"
}

/**
 * Resolves the variable value [url] (the default [defaultUrl] if it is blank): either the directory that has
 * [defaultFile], or the URL of the script itself, whatever it is called (it ends in `.js`, ignoring a query or fragment).
 */
internal fun scriptLocation(url: String?, defaultUrl: String, defaultFile: String): ScriptLocation {
    val value = url?.trim()?.takeIf { it.isNotEmpty() } ?: defaultUrl
    val path = withoutQuery(value)
    if (!path.endsWith(".js")) {
        val dir = value.trimEnd('/')
        return ScriptLocation("$dir/$defaultFile", dir)
    }
    // The directory the files are in, or null if the script is next to the page.
    return ScriptLocation(value, path.substringBeforeLast('/', "").takeIf { '/' in path })
}

internal fun withoutQuery(url: String) = url.substringBefore('#').substringBefore('?')
