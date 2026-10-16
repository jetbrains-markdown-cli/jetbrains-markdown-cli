/*
 * The built-in styles (see [BuiltInStyles]). They are compile-time constants because there is no resource loading
 * in Kotlin/Native, and only style what this tool generates.
 *
 * The IntelliJ and GitHub styles are adapted from github-markdown-css, which is licensed as follows:
 *
 * The MIT License (MIT)
 *
 * Copyright (c) Sindre Sorhus <sindresorhus@gmail.com> (sindresorhus.com)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

/** The title block ([titleBlock]) is pandoc's markup, not the IDE's or GitHub's, so the IntelliJ and GitHub styles share its rules (the pandoc style has pandoc's own). */
private const val TITLE_BLOCK_CSS = """
header#title-block-header {
  margin-bottom: 2em;
}

header#title-block-header p {
  margin: 0 0 0.3em;
  opacity: 0.75;
}

header#title-block-header .abstract {
  margin-top: 1em;
}

header#title-block-header .abstract-title {
  font-weight: bold;
}
"""

/**
 * The style of the IntelliJ Markdown plugin's preview: its `default.css` (adapted from github-markdown-css, without
 * the rules for markup that only exists in the IDE) and the colours it generates from the IDE theme, taken from the
 * IntelliJ Light and Dark themes, which are picked by `prefers-color-scheme`.
 */
internal const val INTELLIJ_CSS = """
/* Based on the preview of IntelliJ's Markdown plugin, which is adapted from github-markdown-css (MIT, Sindre Sorhus) */
:root {
  color-scheme: light dark;
  --default-font-size: 14px;
  --foreground: #080808;
  --background: #ffffff;
  --link: #315fbd;
  --separator: #818594;
  --info: #818594;
  --fence: rgba(212, 222, 231, 0.25);
}

@media (prefers-color-scheme: dark) {
  :root {
    --foreground: #bcbec4;
    --background: #1e1f22;
    --link: #6b9bfa;
    --separator: #6f737a;
    --info: #6f737a;
    --fence: rgba(212, 222, 231, 0.1);
  }
}

body {
  -webkit-text-size-adjust: 100%;
  -ms-text-size-adjust: 100%;
  font-family: Helvetica, Arial, freesans, sans-serif;
  font-size: var(--default-font-size);
  line-height: 1.6;
  word-wrap: break-word;
  margin: 0 2em;
  color: var(--foreground);
  background-color: var(--background);
}

body * {
  box-sizing: border-box;
}

strong {
  font-weight: bold;
}

img {
  border: 0;
  max-width: 100%;
}

input {
  color: inherit;
  font: 13px/1.4 Helvetica, arial, nimbussansl, liberationsans, freesans, clean, sans-serif, "Segoe UI Emoji", "Segoe UI Symbol";
  margin: 0;
}

input[type="checkbox"] {
  appearance: none;
  -webkit-appearance: none;
  width: 14px;
  height: 14px;
  margin: 0;
  padding: 0;
  border: 1px solid currentColor;
  border-radius: 3px;
  position: relative;
  vertical-align: middle;
}

input[type="checkbox"]:checked {
  border-color: var(--link);
  background-color: var(--link);
}

input[type="checkbox"]:checked::after {
  content: "";
  position: absolute;
  left: 3px;
  top: 0;
  width: 4px;
  height: 8px;
  border-style: solid;
  border-color: var(--background);
  border-width: 0 2px 2px 0;
  transform: rotate(45deg);
}

a {
  color: var(--link);
  text-decoration: none;
}

a:hover,
a:active {
  text-decoration: underline;
}

hr {
  height: 1px;
  padding: 0;
  margin: 20px 0;
  border: 0 none;
  background-color: var(--separator);
}

h1,
h2,
h3,
h4,
h5,
h6 {
  position: relative;
  margin-top: 1em;
  margin-bottom: 16px;
  font-weight: bold;
  line-height: 1.4;
}

h1 {
  font-size: 2.2em;
  padding-top: 0.6em;
}

h2 {
  font-size: 1.8em;
  line-height: 1.2;
  padding-top: 0.6em;
}

h3 {
  font-size: 1.3em;
  line-height: 1;
  padding-top: 0.6em;
}

h4,
h5,
h6 {
  font-size: 1em;
}

h6 {
  color: var(--info);
}

p,
blockquote,
ul,
ol,
dl,
table,
pre {
  margin-top: 16px;
  margin-bottom: 16px;
}

ul,
ol {
  padding-left: 2em;
}

ol ol,
ul ol {
  list-style-type: lower-roman;
}

ul ul ol,
ul ol ol,
ol ul ol,
ol ol ol {
  list-style-type: lower-alpha;
}

ul ul,
ul ol,
ol ol,
ol ul {
  margin-top: 0;
  margin-bottom: 0;
}

li > p {
  margin-top: 0;
  margin-bottom: 0;
}

blockquote {
  margin: 16px 0;
  padding: 10px 10px 10px 16px;
  border-left: 2px solid color-mix(in srgb, var(--link) 40%, transparent);
  border-radius: 0 3px 3px 0;
  background-color: var(--fence);
}

blockquote > :first-child {
  margin-top: 0;
}

blockquote > :last-child {
  margin-bottom: 0;
}

table {
  display: block;
  width: 100%;
  overflow: auto;
  word-break: normal;
  border-collapse: collapse;
  border-spacing: 0;
  font-size: 1em;
}

table th {
  font-weight: bold;
}

table th,
table td {
  padding: 6px 13px;
  background: transparent;
  border: 1px solid var(--separator);
}

table tr {
  border-top: 1px solid var(--separator);
}

blockquote table th,
blockquote table td {
  background-color: var(--background);
}

code,
kbd {
  font: 0.9em "JetBrains Mono", Consolas, "Liberation Mono", Menlo, Courier, monospace;
  padding: 0.2em 0.4em;
  margin: 2px;
  border-radius: 3px;
}

code {
  font-weight: inherit;
  font-style: inherit;
  background-color: var(--fence);
}

kbd {
  border: 1px solid var(--separator);
}

pre {
  font: 0.85em "JetBrains Mono", Consolas, "Liberation Mono", Menlo, Courier, monospace;
  padding: 16px;
  overflow: auto;
  line-height: 1.45;
  border-radius: 3px;
  background-color: var(--fence);
}

pre code {
  display: inline;
  max-width: initial;
  padding: 0;
  margin: 0;
  overflow: initial;
  font-size: 100%;
  line-height: inherit;
  word-wrap: normal;
  word-break: normal;
  white-space: pre;
  background: transparent;
  border: 0;
}

.task-list-item {
  list-style-type: none;
}

.task-list-item + .task-list-item {
  margin-top: 3px;
}

.task-list-item input {
  margin: 0 0.35em 0.25em -0.6em;
  vertical-align: middle;
}

span.user-del {
  text-decoration: line-through;
}
""" + TITLE_BLOCK_CSS

/**
 * GitHub's rendering of Markdown, as in github-markdown-css, but for the whole page instead of an element with
 * the class `markdown-body`; the colours switch to the dark theme with `prefers-color-scheme`.
 */
internal const val GITHUB_CSS = """
/* Based on github-markdown-css (MIT, Sindre Sorhus) */
:root {
  color-scheme: light dark;
  --foreground: #1f2328;
  --background: #ffffff;
  --muted: #59636e;
  --link: #0969da;
  --border: #d1d9e0;
  --border-muted: #d1d9e0b3;
  --neutral-muted: #818b981f;
  --subtle: #f6f8fa;
}

@media (prefers-color-scheme: dark) {
  :root {
    --foreground: #f0f6fc;
    --background: #0d1117;
    --muted: #9198a1;
    --link: #4493f8;
    --border: #3d444d;
    --border-muted: #3d444db3;
    --neutral-muted: #656c7633;
    --subtle: #151b23;
  }
}

body {
  -ms-text-size-adjust: 100%;
  -webkit-text-size-adjust: 100%;
  box-sizing: border-box;
  max-width: 980px;
  margin: 0 auto;
  padding: 45px;
  color: var(--foreground);
  background-color: var(--background);
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "Noto Sans", Helvetica, Arial, sans-serif, "Apple Color Emoji", "Segoe UI Emoji";
  font-size: 16px;
  line-height: 1.5;
  word-wrap: break-word;
}

@media (max-width: 767px) {
  body {
    padding: 15px;
  }
}

a {
  color: var(--link);
  text-decoration: none;
  background-color: transparent;
}

a:hover {
  text-decoration: underline;
}

b,
strong,
th {
  font-weight: 600;
}

img {
  max-width: 100%;
  box-sizing: content-box;
  border-style: none;
  background-color: var(--background);
}

hr {
  box-sizing: content-box;
  overflow: hidden;
  height: 0.25em;
  padding: 0;
  margin: 24px 0;
  background-color: var(--border);
  border: 0;
}

h1,
h2,
h3,
h4,
h5,
h6 {
  margin-top: 24px;
  margin-bottom: 16px;
  font-weight: 600;
  line-height: 1.25;
}

h1 {
  padding-bottom: 0.3em;
  font-size: 2em;
  border-bottom: 1px solid var(--border-muted);
}

h2 {
  padding-bottom: 0.3em;
  font-size: 1.5em;
  border-bottom: 1px solid var(--border-muted);
}

h3 {
  font-size: 1.25em;
}

h4 {
  font-size: 1em;
}

h5 {
  font-size: 0.875em;
}

h6 {
  font-size: 0.85em;
  color: var(--muted);
}

p,
blockquote,
ul,
ol,
dl,
table,
pre {
  margin-top: 0;
  margin-bottom: 16px;
}

blockquote {
  margin-left: 0;
  margin-right: 0;
  padding: 0 1em;
  color: var(--muted);
  border-left: 0.25em solid var(--border);
}

blockquote > :first-child {
  margin-top: 0;
}

blockquote > :last-child {
  margin-bottom: 0;
}

ul,
ol {
  padding-left: 2em;
}

ol ol,
ul ol {
  list-style-type: lower-roman;
}

ul ul ol,
ul ol ol,
ol ul ol,
ol ol ol {
  list-style-type: lower-alpha;
}

ul ul,
ul ol,
ol ol,
ol ul {
  margin-top: 0;
  margin-bottom: 0;
}

li > p {
  margin-top: 16px;
}

li + li {
  margin-top: 0.25em;
}

table {
  display: block;
  width: max-content;
  max-width: 100%;
  overflow: auto;
  border-spacing: 0;
  border-collapse: collapse;
}

th,
td {
  padding: 6px 13px;
  border: 1px solid var(--border);
}

tr {
  background-color: var(--background);
  border-top: 1px solid var(--border-muted);
}

tr:nth-child(2n) {
  background-color: var(--subtle);
}

code,
kbd,
pre {
  font-family: ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, "Liberation Mono", monospace;
}

code {
  padding: 0.2em 0.4em;
  margin: 0;
  font-size: 85%;
  white-space: break-spaces;
  background-color: var(--neutral-muted);
  border-radius: 6px;
}

pre {
  padding: 16px;
  overflow: auto;
  font-size: 85%;
  line-height: 1.45;
  color: var(--foreground);
  background-color: var(--subtle);
  border-radius: 6px;
  word-wrap: normal;
}

pre code {
  display: inline;
  max-width: auto;
  padding: 0;
  margin: 0;
  overflow: visible;
  font-size: 100%;
  line-height: inherit;
  word-break: normal;
  white-space: pre;
  background: transparent;
  border: 0;
}

kbd {
  display: inline-block;
  padding: 0.25em 0.4em;
  font-size: 11px;
  line-height: 10px;
  color: var(--foreground);
  vertical-align: middle;
  background-color: var(--subtle);
  border: 1px solid var(--border-muted);
  border-bottom-color: var(--border-muted);
  border-radius: 6px;
  box-shadow: inset 0 -1px 0 var(--border-muted);
}

.task-list-item {
  list-style-type: none;
}

.task-list-item-checkbox {
  margin: 0 0.2em 0.25em -1.4em;
  vertical-align: middle;
}

span.user-del {
  text-decoration: line-through;
}
""" + TITLE_BLOCK_CSS

/**
 * The style of pandoc's default HTML template (`templates/styles.html`, as pandoc 3.7 inserts it into a standalone page
 * without `css`), with its defaults for the template variables that can change it (`fontcolor`, `maxwidth`, ...) filled in,
 * and the rules for the markup that pandoc has no counterpart for in its own template: task lists and strikethrough of
 * the GFM flavour. It is light only, like pandoc's page; its rules for the title block and the table of contents are
 * pandoc's, too, as that markup is pandoc's.
 */
internal const val PANDOC_CSS = """
/* Based on the default style of pandoc's HTML template */
html {
  color: #1a1a1a;
  background-color: #fdfdfd;
}

body {
  margin: 0 auto;
  max-width: 36em;
  padding-left: 50px;
  padding-right: 50px;
  padding-top: 50px;
  padding-bottom: 50px;
  hyphens: auto;
  overflow-wrap: break-word;
  text-rendering: optimizeLegibility;
  font-kerning: normal;
}

@media (max-width: 600px) {
  body {
    font-size: 0.9em;
    padding: 12px;
  }

  h1 {
    font-size: 1.8em;
  }
}

@media print {
  html {
    background-color: white;
  }

  body {
    background-color: transparent;
    color: black;
    font-size: 12pt;
  }

  p, h2, h3 {
    orphans: 3;
    widows: 3;
  }

  h2, h3, h4 {
    page-break-after: avoid;
  }
}

p {
  margin: 1em 0;
}

a {
  color: #1a1a1a;
}

a:visited {
  color: #1a1a1a;
}

img {
  max-width: 100%;
}

svg {
  height: auto;
  max-width: 100%;
}

h1, h2, h3, h4, h5, h6 {
  margin-top: 1.4em;
}

h5, h6 {
  font-size: 1em;
  font-style: italic;
}

h6 {
  font-weight: normal;
}

ol, ul {
  padding-left: 1.7em;
  margin-top: 1em;
}

li > ol, li > ul {
  margin-top: 0;
}

blockquote {
  margin: 1em 0 1em 1.7em;
  padding-left: 1em;
  border-left: 2px solid #e6e6e6;
  color: #606060;
}

div.abstract {
  margin: 2em 2em 2em 2em;
  text-align: left;
  font-size: 85%;
}

div.abstract-title {
  font-weight: bold;
  text-align: center;
  padding: 0;
  margin-bottom: 0.5em;
}

code {
  font-family: Menlo, Monaco, Consolas, 'Lucida Console', monospace;
  font-size: 85%;
  margin: 0;
  hyphens: manual;
}

pre {
  margin: 1em 0;
  overflow: auto;
}

pre code {
  padding: 0;
  overflow: visible;
  overflow-wrap: normal;
}

hr {
  border: none;
  border-top: 1px solid #1a1a1a;
  height: 1px;
  margin: 1em 0;
}

table {
  margin: 1em 0;
  border-collapse: collapse;
  width: 100%;
  overflow-x: auto;
  display: block;
  font-variant-numeric: lining-nums tabular-nums;
}

table caption {
  margin-bottom: 0.75em;
}

tbody {
  margin-top: 0.5em;
  border-top: 1px solid #1a1a1a;
  border-bottom: 1px solid #1a1a1a;
}

th {
  border-top: 1px solid #1a1a1a;
  padding: 0.25em 0.5em 0.25em 0.5em;
}

td {
  padding: 0.125em 0.5em 0.25em 0.5em;
}

header {
  margin-bottom: 4em;
  text-align: center;
}

#TOC li {
  list-style: none;
}

#TOC ul {
  padding-left: 1.3em;
}

#TOC > ul {
  padding-left: 0;
}

#TOC a:not(:hover) {
  text-decoration: none;
}

code {
  white-space: pre-wrap;
}

/* pandoc's rules for task lists (ul.task-list), for the markup of the GFM flavour */
.task-list-item {
  list-style: none;
}

.task-list-item-checkbox {
  font-size: inherit;
  width: 0.8em;
  margin: 0 0.8em 0.2em -1.6em;
  vertical-align: middle;
}

span.user-del {
  text-decoration: line-through;
}
"""
