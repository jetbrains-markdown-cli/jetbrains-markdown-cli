# Changelog

## [1.0.0](https://github.com/jetbrains-markdown-cli/jetbrains-markdown-cli/compare/v0.1.0...v1.0.0) (2026-10-18)


### ⚠ BREAKING CHANGES

* **styles:** pages with a css other than `none` get one more <style> element in front of the others, also with only own stylesheets.
* a standalone document without css now contains the default style; use -V css=none for the previous output.
* the MarkdownCli constructor parameters readInput and write are renamed to readStdin and writeStdout, and writeFile is added.
* the output is no longer wrapped in <body>; use --standalone to get a complete HTML document.
* the output is indented and ends with a newline.
* headings carry an id attribute (e.g. <h1 id="hello">).

### Features

* add --flavour option to select the Markdown flavour ([834eb1d](https://github.com/jetbrains-markdown-cli/jetbrains-markdown-cli/commit/834eb1de72bcf905fc215f961e03efcdb7aa47c7))
* add --metadata-file option to read metadata from a YAML file ([b3daf86](https://github.com/jetbrains-markdown-cli/jetbrains-markdown-cli/commit/b3daf8607ffa7c90a72080ba2313bd2240d87a19))
* add -d/--defaults option to read options from a YAML defaults file ([e44e1f0](https://github.com/jetbrains-markdown-cli/jetbrains-markdown-cli/commit/e44e1f05ce3ea5c15f65c1f1005034a2895bc24b))
* add -V/--variable option to set template variables ([e6aab31](https://github.com/jetbrains-markdown-cli/jetbrains-markdown-cli/commit/e6aab31ff2bada0f26224fa331375504f4dd1b35))
* add -v/--version and show the version in --help ([46553a8](https://github.com/jetbrains-markdown-cli/jetbrains-markdown-cli/commit/46553a8d2895f7403c15ddbcf7264f994f0556c9))
* add a table of contents with --toc, --toc-depth and the toc template variables ([19d50ef](https://github.com/jetbrains-markdown-cli/jetbrains-markdown-cli/commit/19d50efd46cf3820c3030a34f7dee2422e891789))
* add heading ids computed like IntelliJ's Markdown preview ([5d56261](https://github.com/jetbrains-markdown-cli/jetbrains-markdown-cli/commit/5d5626146b70f2c56a13be5bc3c126935b9167d3))
* output only the content by default, add -s/--standalone ([55e8453](https://github.com/jetbrains-markdown-cli/jetbrains-markdown-cli/commit/55e845315117303c953b650f912cac4a625bd365))
* parse command line arguments with Clikt ([51da28e](https://github.com/jetbrains-markdown-cli/jetbrains-markdown-cli/commit/51da28e8cf98649495c24c8c49e8d75ad8b0a4e7))
* pretty-print the HTML output ([4f9ef5d](https://github.com/jetbrains-markdown-cli/jetbrains-markdown-cli/commit/4f9ef5dee23d42fbe92d4c9a324d755a97f43726))
* read input files and write to a file with -o, like pandoc ([92ba955](https://github.com/jetbrains-markdown-cli/jetbrains-markdown-cli/commit/92ba9555e4ab1ec7c09760baa59dc345bfb52283))
* read YAML metadata blocks from the document ([6c46ec3](https://github.com/jetbrains-markdown-cli/jetbrains-markdown-cli/commit/6c46ec3661d7fb7b375bab5387e9cafc2447f9fe))
* **styles:** add the common style, which every css list starts with ([2bdff22](https://github.com/jetbrains-markdown-cli/jetbrains-markdown-cli/commit/2bdff224ad5c9df28ec8849cc69f5bb865c658ac))
* **styles:** add the gitlab built-in style ([445c852](https://github.com/jetbrains-markdown-cli/jetbrains-markdown-cli/commit/445c8528b7e7387fc026d1c814ceb0f22e0ce7f9))
* **styles:** add the pandoc built-in style ([036abb1](https://github.com/jetbrains-markdown-cli/jetbrains-markdown-cli/commit/036abb182229561ad2fefce7beeca6446af793aa))
* **styles:** load web fonts for the intellij, github and gitlab styles ([4188f21](https://github.com/jetbrains-markdown-cli/jetbrains-markdown-cli/commit/4188f2106a39660c6a22743fc682193eb8f8339e))
* use the abstract and abstract-title template variables in the title block ([6471efc](https://github.com/jetbrains-markdown-cli/jetbrains-markdown-cli/commit/6471efc3bc83c6a4da5e673fc40722a0790ddf9a))
* use the author template variable in a title block and meta tags ([438eb57](https://github.com/jetbrains-markdown-cli/jetbrains-markdown-cli/commit/438eb57c25095e3c4b3e55e3ab0e887e4aaf54de))
* use the css template variable for built-in styles and stylesheet links ([92060e4](https://github.com/jetbrains-markdown-cli/jetbrains-markdown-cli/commit/92060e46b5c545a879ee750cc2636d0ac3d04800))
* use the date template variable in the title block and a meta tag ([e4bab00](https://github.com/jetbrains-markdown-cli/jetbrains-markdown-cli/commit/e4bab0090ce5bcf027bcf5ae6f29ec7eae058742))
* use the highlight template variables to highlight code fences with highlight.js ([cb6351d](https://github.com/jetbrains-markdown-cli/jetbrains-markdown-cli/commit/cb6351d2db6a65db5a79575f6c39dfbbd5037ed2))
* use the lang template variable as the lang attribute of html ([f292902](https://github.com/jetbrains-markdown-cli/jetbrains-markdown-cli/commit/f292902d2812392de85c105902d1085363abe4c1))
* use the mermaid template variables to render mermaid diagrams ([3e0ad10](https://github.com/jetbrains-markdown-cli/jetbrains-markdown-cli/commit/3e0ad1094a329acf0920e73f10e50a28636a87d5))
* use the subtitle template variable in the title block ([0a2e36d](https://github.com/jetbrains-markdown-cli/jetbrains-markdown-cli/commit/0a2e36db32d7c924e857ce2991c657d5a848c7e7))
* use the title template variable as the document title ([d5858d0](https://github.com/jetbrains-markdown-cli/jetbrains-markdown-cli/commit/d5858d06c196f091c96463e51f722907598bef9d))


### Miscellaneous Chores

* release 1.0.0 ([77e4408](https://github.com/jetbrains-markdown-cli/jetbrains-markdown-cli/commit/77e4408d7e2d3dd152229e4eb69bc220e732e58d))
