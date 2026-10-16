# AGENTS.md

Kotlin/Native command line tool: reads GitHub Flavored Markdown on stdin, writes HTML to stdout.
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
- Code fences / syntax highlighting: `ui/preview/html/DefaultCodeFenceGeneratingProvider.kt`,
  `extensions/common/highlighter/` (and `HtmlSyntaxHighlighter.kt` in `platform/markdown-utils` of intellij-community)

## Layout

- `src/commonMain/kotlin/Markdown.kt`: `markdownToHtml()`.
- `src/nativeMain/kotlin/Main.kt`: stdin → `markdownToHtml` → stdout.
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

- Input is normalized to LF before parsing (the library requires it); keep that.
- CI smoke tests (`build.yml`) assert on exact output fragments such as `<h1>Hello</h1>`;
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
