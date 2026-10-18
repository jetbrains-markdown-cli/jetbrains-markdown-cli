# AGENTS.md

Kotlin/Native command line tool: reads Markdown (GFM by default) on stdin, writes HTML to stdout (a complete document with `--standalone`).
The goal is to be a replacement for `pandoc` when converting Markdown to HTML, using the JetBrains/markdown library,
for developers who want the same output as the JetBrains IDEs show in their preview. So the command line follows pandoc
(input/output handling, `--standalone`, template variables, defaults and metadata files), while the HTML is as close as
sensible to what IntelliJ's *Markdown → HTML export* produces (minus IDE-only features, see
[Differences from the IDE export](#differences-from-the-ide-export)). It is not meant to cover all of pandoc;
the README's *Limitations* section lists what is missing compared to the IDE and to pandoc, keep it up to date.

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

- `action.yml`: the composite GitHub Action for other repositories: downloads the release binary of the runner's platform (`version` input, default
  latest), puts it on the `PATH` and runs it with the inputs mapped to options (inputs go through `env:`, never interpolated into the scripts).
  Keep its inputs in sync with the README's *GitHub Action* section, and the asset names with the `Package` step of `build.yml`.
- `src/commonMain/kotlin/Markdown.kt`: `markdownToHtml()`, the `Flavour` enum (commonmark/gfm/space, default = GFM, like the plugin's `MarkdownDefaultFlavour`), the flavour wrapper adding anchored headings and, with `standalone`, the document skeleton.
- `src/commonMain/kotlin/HeadingAnchors.kt`: port of the plugin's heading anchor logic.
- `src/commonMain/kotlin/TitleBlock.kt`: the title block at the start of the `<body>` and the `<meta>` tags, like pandoc's HTML template.
- `src/commonMain/kotlin/TableOfContents.kt`: the `<nav id="TOC">` after the title block; the entries come from `--toc`, the `toc` variable only switches the nav, like in pandoc's template.
- `src/commonMain/kotlin/Styles.kt`: the `css` template variable: built-in style names (`none`, `common`, `default`, `intellij`, `github`, `gitlab`, `pandoc`) to `<style>`, other values to `<link>`. `common` (base print/image rules) is put in front of every list unless it has `none` or `common`; it is not a page style (not in `BuiltInStyles.css`), so highlight/mermaid ignore it when deriving their look from "the last built-in style".
- `src/commonMain/kotlin/Highlight.kt`: the `highlight*` template variables: the highlight.js `<link>`s (before the `css` entries), the box rules, and the deferred `<script>`s (after).
- `src/commonMain/kotlin/Mermaid.kt`: the `mermaid*` template variables: a `<style>` for the diagrams (before the `css` entries) and the deferred `<script>`s (after).
- `src/commonMain/kotlin/ScriptLocation.kt`: the "directory or script URL" of `highlight-url` and `mermaid-url`.
- `src/commonMain/kotlin/StyleSheets.kt`: the CSS of the built-in styles, as `const val`s (no resource loading in Kotlin/Native).
- `src/commonMain/kotlin/PrettyTagRenderer.kt`: indents block tags; leaves `<pre>` content untouched.
- `src/commonMain/kotlin/TemplateVariables.kt`: `TemplateValue`/`TemplateContext` (pandoc-style template variables) and the `-V` parsing.
- `src/commonMain/kotlin/Metadata.kt`: YAML (via [snakeyaml-engine-kmp](https://github.com/krzema12/snakeyaml-engine-kmp)) to `TemplateValue`s, merging of metadata layers, and extracting the document's YAML metadata blocks.
- `src/commonMain/kotlin/Settings.kt`: `Settings`, the options a defaults file can set; defaults files and the command line are layered with `Settings.then`.
- `src/commonMain/kotlin/Version.kt`: `VERSION`, shown by `--version` and `--help`. The only place with the version: release-please rewrites the line annotated `x-release-please-version` (see `release-please-config.json`), so do not edit it by hand.
- `src/commonMain/kotlin/MarkdownCli.kt`: the command line interface ([Clikt](https://github.com/ajalt/clikt)); options are declared
  here. Input/output (stdin, stdout, files) are injected so it can be tested without them.
  Input files and `-o` work like pandoc's (no files = stdin, no `-o` = stdout, `-` = stdin/stdout, several files are
  concatenated with blank lines); the output file is only written once the conversion succeeded.
- `src/nativeMain/kotlin/Main.kt`: reads stdin and files, writes files, and runs `MarkdownCli`.
- `src/commonTest/kotlin/MarkdownTest.kt`: tests (run on the host target).
- `docs/defaults.yaml`, `docs/metadata.yaml`: the defaults and metadata file that build `README.html` from the README, see [Eat your own dogfood](#eat-your-own-dogfood).
- `.github/workflows/build.yml`: builds all targets, smoke-tests them, and keeps the release PR up to date with release-please. Once that PR is merged, the `release` job attaches the binaries (archives with `LICENSE` and `THIRD-PARTY`, which is also a release asset) to the release release-please created. The `test-action` job runs `action.yml` on all three OSes against the latest release; the `release` job also moves the major tag (`v1`) that `uses: ...@v1` resolves to, and builds the README as HTML with the action (`docs/defaults`) and as PDF with Chrome, attached as `README.pdf`. Everything is in this one workflow because tags and releases made with `GITHUB_TOKEN` do not trigger other workflows.
- `release-please-config.json`, `.release-please-manifest.json`: release-please (type `simple`, tags `vX.Y.Z`): the version and `CHANGELOG.md` come from the Conventional Commits on `main`, and `Version.kt` is its only extra file. The first release is forced to 1.0.0 by a `Release-As: 1.0.0` footer. Never edit the manifest or `CHANGELOG.md` by hand.
- `.github/dependabot.yml`: weekly updates of the GitHub Actions and the Gradle dependencies/wrapper (prefixes `ci` and `build(deps)`). After a library update check `THIRD-PARTY`; the pinned font URLs are not covered.
- `THIRD-PARTY`: the notices and license texts that the release archives must carry.

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
  (like `-M`) > the document's YAML blocks > metadata files. Consumed so far, in `markdownToHtml`: `title`, `subtitle`, `author`, `date`, `abstract`, `lang`, `abstract-title`, `css`, `highlight`, `highlight-style`, `highlight-style-dark`, `highlight-url`, `highlight-languages`, `mermaid`, `mermaid-theme`, `mermaid-theme-dark`, `mermaid-url`, `toc`, `toc-title` (`--toc`/`--toc-depth` are options, not variables, as in pandoc).
- Heading anchors must stay in sync with the plugin's algorithm (lowercase, drop everything but letters, digits,
  `-`, `_` and spaces, spaces → `-`, duplicates get `-1`, `-2`, ...). If the plugin changes, mirror it.
- CI smoke tests (`build.yml`) assert on exact output fragments such as `<h1 id="hello">Hello</h1>`;
  update them together with any output format change.

## Differences from the IDE export

Intentionally not implemented, because they only serve the IDE's live preview: `md-src-pos` attributes, per-line
`<span>`s in paragraphs, code-fence copy/run buttons, resolving relative links to `file:` URLs, scripts,
and the Content-Security-Policy `<meta>`.

Web fonts: the `intellij`, `github` and `gitlab` styles start with `@font-face` rules for `woff2` files on jsDelivr (pinned versions of
`@gitlab/fonts` and the fontsource variable packages `noto-sans` and `arimo`; the Noto Sans and Arimo rules are fontsource's own
`wght[-italic].css`, cut down to the latin, latin-ext, cyrillic and greek subsets). They must not shadow fonts that may be installed: the
families `Noto Sans Variable` and `Arimo Variable` are listed *after* the system names in the stacks, `GitLab Sans`/`GitLab Mono` and
`JetBrains Mono` are first because the original stacks have them first. Check with a headless Chromium and `document.fonts` that the
files load (force the family with `!important`, as this machine may have the system fonts). Update the versions in the URLs together.

Styles: the `intellij` style is the plugin's `default.css` (`core/resources/.../extensions/common/baseStyles/`) without
the rules for IDE-only markup (octicon anchors, front matter header, alerts) plus the colours that
`PreviewLAFThemeStyles` generates from the IDE theme, here fixed to the IntelliJ Light/Dark theme values
(`expUI_light`/`expUI_dark` in `platform-resources`) and switched with `prefers-color-scheme`. Mirror changes of that
CSS when the plugin changes. Alerts (`[!NOTE]`) are not rendered by this tool, so they are not styled either.

Diagram and formula rendering is only implemented for Mermaid, and only in the browser: PlantUML and LaTeX math fences stay plain
code blocks. The opt-in `mermaid` variable adds Mermaid to a standalone page (`Mermaid.kt`, no change to the body: the script
looks for `<code class="language-mermaid">` and replaces its `<pre>` by a `<div class="mermaid">`). Things to keep in mind there:

- Order in `<head>`: the `<style>` before the `css` entries, the scripts after them, like with `highlight`.
- Diagrams that `mermaid.parse` rejects stay code blocks (no error drawing, no leftover element Mermaid makes on a failed `render`).
- Mermaid has the theme as a global setting and bakes it into the SVG, so diagrams are drawn one after the other and again when
  `prefers-color-scheme` changes. Themes are validated against Mermaid's list because they are embedded in the script.
- `mermaid-url` works like `highlight-url` (`ScriptLocation.kt`), with `mermaid.min.js` (the classic script, not the ES module).
- Check a change in a real browser with a local copy of `mermaid.min.js` (`mermaid-url`); headless Chromium does not switch to dark by
  itself, so the re-drawing on a scheme change is not covered by that.

Syntax highlighting like the IDE's is not implemented. The plugin does it with IntelliJ's own lexers and the editor colour
scheme (inline `style="color: ..."` per token), which cannot be reused here. Fences are emitted as
`<code class="language-xyz">`. The opt-in `highlight` variable adds highlight.js to a standalone page instead
(`Highlight.kt`, no change to the body): the output of the fences must stay as it is, because highlight.js finds that class
itself. Things to keep in mind there:

- Order in `<head>`: highlight.js `<link>`s, the box `<style>`, the `css` entries, the scripts. Do not move the links
  behind `css`, which would take the right of overriding them away from the user's stylesheets.
- The script only highlights `pre > code[class*="language-"]` whose language `hljs.getLanguage` knows (no guessing for
  plain fences, no console warnings for `mermaid`).
- Default styles are derived from the last built-in `css` style; the table is in the README and in `Highlight.kt`.
- `highlight-url` is either a directory (`highlight.min.js` in it, like the `@highlightjs/cdn-assets` package, cdnjs, unpkg) or the URL of the
  script (a `.js` URL, with `styles/` next to it). The default is the unpinned official package, so it follows new major versions. Names that go into URLs are validated.
- Check a change in a real browser (headless Chromium with `--dump-dom` and `--screenshot` works), not just by tests.

## Eat your own dogfood

This repository is its own example: the README is a Markdown document that is converted to HTML with this tool, using
its own defaults file and metadata file, and no other converter. The pieces: the YAML metadata block at the top of
`README.md` (`title`, `subtitle`, `author`, `abstract`), `docs/defaults.yaml` (input `README.md`, output `README.html`,
`standalone`, `toc`, `highlight`, `mermaid`, and the metadata file) and `docs/metadata.yaml` (`css: github`, `lang`,
`toc-title`). Build the page from the repository root, as the paths in a defaults file are relative to the working directory:

```sh
./build/bin/linuxX64/releaseExecutable/jetbrains-markdown-cli.kexe -d docs/defaults   # writes README.html, which is git-ignored
```

- Build the page with the freshly built binary before finishing a change that touches the output, a template variable,
  an option, a style or the README, and look at the result (the generated HTML, and a headless Chromium screenshot for
  styles, highlighting and diagrams). If the page breaks, the change is not done.
- Every new option, template variable, defaults file key or built-in style that the page could use must be used there
  (in the README's YAML block, the defaults file or the metadata file), unless it is meant to be off by default or would make
  the page worse; the repository is the example people copy from.
- Use only features that the tool implements. A workaround for something missing is a finding to report or fix in the
  tool, not something to hide in the example. Keep the README renderable on GitHub and in the IDE preview as well.
- The page is written to the repository root, so relative links of the README (`LICENSE`, `THIRD-PARTY`, ...) keep working;
  that is why `/README.html` is in `.gitignore` and not committed.
- `buildsAPageLikeTheReadmeOfThisRepository` in `MarkdownTest.kt` mirrors this setup in small (the tests cannot read the real files):
  adjust it when the files in `docs/` change. CI does not build the page yet.

## Conventions

- Keep the README's usage example in sync with the actual output, and its *Limitations* section with what is implemented
  (e.g. when another template variable or option gets supported).
- Add or adjust a test in `MarkdownTest.kt` for every output change.
- Keep `THIRD-PARTY` (licenses of the libraries linked into the binaries, of the code and styles adapted from other projects,
  and the Kotlin/Native runtime) in sync with `build.gradle.kts` and `StyleSheets.kt`: new or updated dependencies, new built-in
  styles, ported code. CI puts it, with `LICENSE`, into every release archive and attaches it to the release.
- Commit messages follow [Conventional Commits](https://www.conventionalcommits.org/): `type(scope): summary`
  with an imperative, lowercase summary (types such as `feat`, `fix`, `docs`, `test`, `ci`, `build`, `refactor`,
  `chore`; scope optional). Mark breaking output changes with `!` or a `BREAKING CHANGE:` footer. release-please derives the next version and the changelog from them: `feat` is a minor, `fix` a patch,
  `!`/`BREAKING CHANGE:` a major bump, and only `feat`, `fix` (and `perf`, `revert`) reach the changelog, so use `feat`/`fix`
  for what users notice.
