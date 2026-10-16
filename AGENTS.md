# AGENTS.md

Kotlin/Native command line tool: reads Markdown (GFM by default) on stdin, writes HTML to stdout (a complete document with `--standalone`).
The goal is to produce HTML as close as sensible to what IntelliJ's *Markdown → HTML export* produces
(minus IDE-only features, see [Differences from the IDE export](#differences-from-the-ide-export)).

## Related repositories

| Role | Repository | Used for |
|------|------------|----------|
| This project | [jetbrains-markdown-cli/jetbrains-markdown-cli](https://github.com/jetbrains-markdown-cli/jetbrains-markdown-cli) (`main`) | the CLI |
| Parser/renderer library (upstream) | [JetBrains/markdown](https://github.com/JetBrains/markdown) | consumed as `org.jetbrains:markdown` from Maven Central (see `build.gradle.kts` for the version) |
| ... fork | [jetbrains-markdown-cli/jetbrains-markdown](https://github.com/jetbrains-markdown-cli/jetbrains-markdown) (`master`) | reference checkout for the library's API/source |
| IntelliJ (upstream) | [JetBrains/intellij-community](https://github.com/JetBrains/intellij-community) | contains the Markdown plugin we mirror, under `plugins/markdown/` |
| ... fork | [jetbrains-markdown-cli/intellij-community](https://github.com/jetbrains-markdown-cli/intellij-community) (`master`) | reference checkout of the plugin |

The forks are only read for reference; this repo does not build against them. The library's published klibs ship
no sources, so look up the library API in its repository (or let the compiler tell you).

Where to look in the plugin (all under `plugins/markdown/core/src/org/intellij/plugins/markdown/` unless noted):

- HTML generation setup: `lang/parser/MarkdownDefaultFlavour.kt`
- Heading `id`s: `ui/preview/html/HeaderGeneratingProvider.kt`, `HeaderAnchorCache.kt`, `lang/psi/impl/MarkdownHeader.kt`
- Code fences / syntax highlighting: `ui/preview/html/DefaultCodeFenceGeneratingProvider.kt`,
  `extensions/common/highlighter/` (and `HtmlSyntaxHighlighter.kt` in `platform/markdown-utils` of intellij-community)
- Page skeleton and export: `plugins/markdown/jcef/src/com/intellij/markdown/jcef/preview/MarkdownJCEFHtmlPanel.kt`
  (`buildIndexResource`) and `.../HtmlExporter.kt` (Jsoup pretty printing)

## Layout

- `src/commonMain/kotlin/Markdown.kt`: `markdownToHtml()`, the `Flavour` enum (commonmark/gfm/space, default = GFM, like the plugin's `MarkdownDefaultFlavour`), the flavour wrapper adding anchored headings and, with `standalone`, the document skeleton.
- `src/commonMain/kotlin/HeadingAnchors.kt`: port of the plugin's heading anchor logic.
- `src/commonMain/kotlin/PrettyTagRenderer.kt`: indents block tags; leaves `<pre>` content untouched.
- `src/commonMain/kotlin/TemplateVariables.kt`: `TemplateValue`/`TemplateContext` (pandoc-style template variables) and the `-V` parsing.
- `src/commonMain/kotlin/Metadata.kt`: YAML (via [snakeyaml-engine-kmp](https://github.com/krzema12/snakeyaml-engine-kmp)) to `TemplateValue`s, merging of metadata layers, and extracting the document's YAML metadata blocks.
- `src/commonMain/kotlin/Settings.kt`: `Settings`, the options a defaults file can set; defaults files and the command line are layered with `Settings.then`.
- `src/commonMain/kotlin/MarkdownCli.kt`: the command line interface ([Clikt](https://github.com/ajalt/clikt)); options are declared
  here. Input/output (stdin, stdout, files) are injected so it can be tested without them.
  Input files and `-o` work like pandoc's (no files = stdin, no `-o` = stdout, `-` = stdin/stdout, several files are
  concatenated with blank lines); the output file is only written once the conversion succeeded.
- `src/nativeMain/kotlin/Main.kt`: reads stdin and files, writes files, and runs `MarkdownCli`.
- `src/commonTest/kotlin/MarkdownTest.kt`: tests (run on the host target).
- `.github/workflows/build.yml`: builds all targets, smoke-tests them, publishes binaries on `v*` tags.

All logic lives in `commonMain`; keep `nativeMain` to I/O only.

## Build and test

```sh
./gradlew linuxX64Test                   # tests (host target; use the matching target on other OSes)
./gradlew linkReleaseExecutableLinuxX64  # binary: build/bin/linuxX64/releaseExecutable/jetbrains-markdown-cli.kexe
./build/bin/linuxX64/releaseExecutable/jetbrains-markdown-cli.kexe < README.md
```

Targets are limited to those JetBrains/markdown publishes Kotlin/Native artifacts for: linuxX64, linuxArm64,
mingwX64, macosX64, macosArm64. macOS targets need a macOS host; Kotlin/Native cannot run on arm64 Linux hosts, so
linuxArm64 is cross-compiled and smoke-tested by a separate CI job. Locally you can only run linuxX64 (on Linux x64).

## Gotchas

- **Kotlin/Native regex is not `java.util.regex`.** `\p{IsAlphabetic}` throws, and negated classes containing
  `\p{..}` (e.g. `[^\p{L}0-9_ -]`) silently match the wrong characters. Filter by `Char.category` instead
  (see `HeadingAnchors.kt`). Verify any new regex on a native target, not just by reading it.
- **The generator emits some tags as raw HTML** (`<pre>`, `<thead>`, `<tbody>`, HTML blocks) instead of through
  `TagRenderer.openTag/closeTag`, so `PrettyTagRenderer.printHtml` has to recognize them. If indentation looks off
  for a new construct, check which of the two paths it takes.
- The library wraps the document in `<body>` (the `MARKDOWN_FILE` provider). Without `--standalone` that provider is
  removed and the output is just the content (like `pandoc` without `-s`); the `<head>` is added by `markdownToHtml`.
- **Clikt 5.1.0 and the native compiler cache:** linking the *debug test* binary fails with `duplicate symbol ...
  selfAndAncestors` (defined in both `clikt` and `clikt-mordant`). `build.gradle.kts` therefore calls
  `disableNativeCache` for test executables only; release binaries link fine. Re-check when updating Clikt or Kotlin.
- Input is normalized to LF before parsing (the library requires it); keep that.
- **Template variable precedence follows pandoc** (checked against pandoc 3.7): `-V`/`variables` > `metadata:` of defaults files
  (like `-M`) > the document's YAML blocks > metadata files. Only `title` is consumed so far, in `markdownToHtml`.
- Heading anchors must stay in sync with the plugin's algorithm (lowercase, drop everything but letters, digits,
  `-`, `_` and spaces, spaces → `-`, duplicates get `-1`, `-2`, ...). If the plugin changes, mirror it.
- CI smoke tests (`build.yml`) assert on exact output fragments such as `<h1 id="hello">Hello</h1>`;
  update them together with any output format change.

## Differences from the IDE export

Intentionally not implemented, because they only serve the IDE's live preview: `md-src-pos` attributes, per-line
`<span>`s in paragraphs, code-fence copy/run buttons, resolving relative links to `file:` URLs, styles/scripts,
and the Content-Security-Policy `<meta>`.

Syntax highlighting is not implemented. The plugin does it with IntelliJ's own lexers and the editor colour scheme
(inline `style="color: ..."` per token), which cannot be reused here. Fences are emitted as
`<code class="language-xyz">` so a client-side highlighter (highlight.js, Prism) can be added by the consumer.

## Conventions

- Keep the README's usage example in sync with the actual output.
- Add or adjust a test in `MarkdownTest.kt` for every output change.
- Commit messages follow [Conventional Commits](https://www.conventionalcommits.org/): `type(scope): summary`
  with an imperative, lowercase summary (types such as `feat`, `fix`, `docs`, `test`, `ci`, `build`, `refactor`,
  `chore`; scope optional). Mark breaking output changes with `!` or a `BREAKING CHANGE:` footer.
