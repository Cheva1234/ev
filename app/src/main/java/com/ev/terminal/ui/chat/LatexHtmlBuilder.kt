package com.ev.terminal.ui.chat

/** Builds a self-contained offline KaTeX document from safe text and math delimiters. */
object LatexHtmlBuilder {

    private val mathPattern = Regex("\\$\\$[\\s\\S]+?\\$\\$|\\$[^$\\n]+?\\$")

    fun containsMath(text: String): Boolean = mathPattern.containsMatchIn(text)

    fun build(text: String): String {
        val body = StringBuilder()
        var cursor = 0
        mathPattern.findAll(text).forEach { match ->
            appendText(body, text.substring(cursor, match.range.first))
            val source = match.value
            val display = source.startsWith("$$")
            val expression = source.substring(if (display) 2 else 1, source.length - if (display) 2 else 1)
            val className = if (display) "math math-block" else "math math-inline"
            body.append("<span class=\"")
                .append(className)
                .append("\">")
                .append(escapeHtml(expression))
                .append("</span>")
            cursor = match.range.last + 1
        }
        appendText(body, text.substring(cursor))

        return """
            <!doctype html>
            <html><head>
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <link rel="stylesheet" href="katex.min.css">
              <style>
                html, body { margin: 0; padding: 0; background: transparent; }
                body { color: #eeeeee; font-family: monospace; font-size: 14px; line-height: 1.45; }
                .math { color: #00d9ff; }
                .math-block { display: block; margin: 0.5em 0; text-align: left; }
                .katex { color: inherit; font-size: 1.1em; }
              </style>
            </head><body>
              <div id="content">$body</div>
              <script src="katex.min.js"></script>
              <script>
                document.querySelectorAll('.math').forEach(function (element) {
                  var displayMode = element.classList.contains('math-block');
                  katex.render(element.textContent, element, {
                    displayMode: displayMode,
                    throwOnError: false,
                    output: 'html'
                  });
                });
              </script>
            </body></html>
        """.trimIndent()
    }

    private fun appendText(builder: StringBuilder, text: String) {
        builder.append(escapeHtml(text).replace("\n", "<br>"))
    }

    private fun escapeHtml(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")
}
