# jetbrains-markdown-cli

Command line wrapper around [JetBrains/markdown](https://github.com/JetBrains/markdown).

Reads Markdown from stdin and writes HTML to stdout:

```sh
echo '# Hello *world*' | jetbrains-markdown-cli
# <h1 id="hello-world">Hello <em>world</em></h1>
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

The `<title>` is the text of the first heading.

## Options

| Option                         | Description |
|--------------------------------|-------------|
| `-s`, `--standalone`           | Produce a complete HTML document (doctype, `<head>`, `<body>`) instead of just the content |
| `-f`, `--flavour=...`          | Markdown flavour: `gfm` (default, GitHub Flavored Markdown like IntelliJ's Markdown plugin), `commonmark` (plain CommonMark) or `space` (JetBrains Space) |
| `-V`, `--variable=KEY[=VAL]`   | Set a template variable like `pandoc --variable`: without a value it is `true`, repeating a key makes a list. Can be repeated |
| `-h`, `--help`                 | Show the help and exit |

```sh
printf '| a |\n|---|\n| 1 |\n' | jetbrains-markdown-cli --flavour commonmark   # no <table>
```

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
