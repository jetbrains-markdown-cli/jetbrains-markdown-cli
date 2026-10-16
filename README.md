# jetbrains-markdown-cli

Command line wrapper around [JetBrains/markdown](https://github.com/JetBrains/markdown).

Converts Markdown files to HTML, with the input and output handled like in [pandoc](https://pandoc.org/MANUAL.html#using-pandoc):

```sh
jetbrains-markdown-cli -o hello.html hello.md   # file to file
jetbrains-markdown-cli chap1.md chap2.md        # several files, concatenated with blank lines between them; HTML on stdout
```

Without input files Markdown is read from stdin, and without `-o` the HTML is written to stdout, which needs no mounts
in a container. The file name `-` also stands for stdin (as an input) and stdout (as the `-o` argument):

```sh
echo '# Hello *world*' | jetbrains-markdown-cli
# <h1 id="hello-world">Hello <em>world</em></h1>
echo '# Hello *world*' | jetbrains-markdown-cli intro.md - -o -   # intro.md, then stdin, to stdout
```

The output is indented, and headings get `id`s computed like in IntelliJ's Markdown preview.

With `-s`/`--standalone` the output is a complete HTML document (like `pandoc --standalone`) instead of just the content:

```sh
echo '# Hello *world*' | jetbrains-markdown-cli --standalone
# <!DOCTYPE html>
# <html>
#   <head>
#     <meta charset="UTF-8">
#     <title>Hello *world*</title>
#   </head>
#   <body>
#     <h1 id="hello-world">Hello <em>world</em></h1>
#   </body>
# </html>
```

The `<title>` is the `title` [template variable](#template-variables), or else the text of the first heading.

## Options

| Option                         | Description |
|--------------------------------|-------------|
| `INPUT...`                     | The Markdown files to convert. They are concatenated, with a blank line between them, before parsing (so a YAML metadata file can be one of them, like in pandoc). Stdin if there are none; `-` is stdin |
| `-o`, `--output=FILE`          | Write the HTML to `FILE` (replacing it) instead of stdout; `-` is stdout. Nothing is written if the conversion fails |
| `-s`, `--standalone`           | Produce a complete HTML document (doctype, `<head>`, `<body>`) instead of just the content |
| `-f`, `--flavour=...`          | Markdown flavour: `gfm` (default, GitHub Flavored Markdown like IntelliJ's Markdown plugin), `commonmark` (plain CommonMark) or `space` (JetBrains Space) |
| `-V`, `--variable=KEY[=VAL]`   | Set a template variable like `pandoc --variable`: without a value it is `true`, repeating a key makes a list. Can be repeated |
| `-d`, `--defaults=FILE`        | Read options from a YAML defaults file like `pandoc --defaults` (`input-files`, `output-file`, `standalone`, `flavour`, `variables`, `metadata`, `metadata-files`; other keys are ignored with a warning). `FILE.yaml` is found as `FILE`. Can be repeated, later files override earlier ones, the command line overrides them all (input files on the command line replace those of defaults files, like in pandoc) |
| `--metadata-file=FILE`         | Read metadata (a YAML mapping) from `FILE`. Can be repeated, later files override earlier ones |
| `-h`, `--help`                 | Show the help and exit |

```sh
printf '| a |\n|---|\n| 1 |\n' | jetbrains-markdown-cli --flavour commonmark   # no <table>
```

## Template variables

Like in pandoc, the document skeleton of `--standalone` output has template variables, which can be set in four ways
(highest precedence first):

| Method | Example |
|--------|---------|
| `-V`/`--variable` on the command line, or `variables:` in a [defaults file](https://pandoc.org/MANUAL.html#default-files) | `-V title="My document"` |
| `metadata:` in a defaults file | `metadata: {title: My document}` |
| A [YAML metadata block](https://pandoc.org/MANUAL.html#extension-yaml_metadata_block) in the document (not part of the output) | `---`, `title: My document`, `---` at the start of the input |
| `--metadata-file` on the command line, or `metadata-files:` in a defaults file | `--metadata-file meta.yaml` |

```sh
printf -- '---\ntitle: My *document*\n---\n\n# Hello\n' | jetbrains-markdown-cli --standalone | grep title
#     <title>My document</title>
printf '# Hello\n' | jetbrains-markdown-cli --standalone -V 'title=Mine & yours' | grep title
#     <title>Mine &amp; yours</title>
```

Within a method, later settings override earlier ones, and a field of a later layer replaces the whole field of an
earlier one. The command line overrides defaults files, for variables as well (pandoc would append the command line
values to a list).

### Supported variables

| Variable | Used for |
|----------|----------|
| `title`  | The `<title>`, as plain text: it is Markdown, so `My *title*` becomes `My title`. Without it the text of the first heading is used |

Not (yet) like pandoc: `--file-scope`, the `sourcefile`/`outputfile` variables, `-M`/`--metadata`, `pagetitle`, the title block in the `<body>`, and finding defaults files in
the user data directory. Variables are escaped when inserted into the HTML, while pandoc inserts `--variable` values as they are.

## Downloads

Native binaries (no JVM required) are attached to each [release](../../releases), built by GitHub Actions
for every platform the Kotlin/Native toolchain and JetBrains/markdown support:

| OS      | Architectures |
|---------|---------------|
| Linux   | amd64, arm64  |
| macOS   | amd64, arm64  |
| Windows | amd64         |

Other platforms (Linux arm/i386/loong64/ppc64le/s390x/riscv64, Windows arm64) are not supported:
JetBrains/markdown publishes no Kotlin/Native artifacts for them and Kotlin/Native has no (stable) target for them.

## Building

```sh
./gradlew linkReleaseExecutableLinuxX64   # or ...LinuxArm64, ...MingwX64, ...MacosX64, ...MacosArm64
./gradlew linuxX64Test                    # run tests on the host target
```

The binary ends up in `build/bin/<target>/releaseExecutable/`. macOS targets need a macOS host.
