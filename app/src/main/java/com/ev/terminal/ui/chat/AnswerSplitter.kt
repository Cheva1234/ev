package com.ev.terminal.ui.chat

/**
 * Splits a long model answer into multiple chat bubbles so the chat stays
 * readable on a phone screen.
 *
 * Strategy: group paragraphs into bubbles up to [MAX_BUBBLE_CHARS]; a paragraph
 * that is itself too long is split by lines, and a single over-long line is
 * split on word boundaries (hard-sliced only for a single unbreakable word).
 */
object AnswerSplitter {

    const val MAX_BUBBLE_CHARS = 1000

    fun split(text: String): List<String> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()

        val parts = mutableListOf<String>()
        val current = StringBuilder()

        fun flush() {
            if (current.isNotBlank()) {
                parts += current.toString().trim()
                current.clear()
            }
        }

        for (paragraph in trimmed.split(Regex("\\n\\s*\\n"))) {
            val para = paragraph.trim()
            if (para.isEmpty()) continue
            when {
                para.length > MAX_BUBBLE_CHARS -> {
                    flush()
                    parts += splitLongBlock(para)
                }
                current.length + para.length + 2 > MAX_BUBBLE_CHARS -> {
                    flush()
                    current.append(para)
                }
                else -> {
                    if (current.isNotBlank()) current.append("\n\n")
                    current.append(para)
                }
            }
        }
        flush()
        return parts
    }

    private fun splitLongBlock(block: String): List<String> {
        val parts = mutableListOf<String>()
        val buf = StringBuilder()
        for (line in block.split("\n")) {
            when {
                line.length > MAX_BUBBLE_CHARS -> {
                    if (buf.isNotBlank()) {
                        parts += buf.toString().trim()
                        buf.clear()
                    }
                    parts += chunkLine(line)
                }
                buf.length + line.length + 1 > MAX_BUBBLE_CHARS -> {
                    parts += buf.toString().trim()
                    buf.clear()
                    buf.append(line)
                }
                else -> {
                    if (buf.isNotBlank()) buf.append("\n")
                    buf.append(line)
                }
            }
        }
        if (buf.isNotBlank()) parts += buf.toString().trim()
        return parts
    }

    private fun chunkLine(line: String): List<String> {
        val chunks = mutableListOf<String>()
        val buf = StringBuilder()
        for (word in line.split(" ")) {
            when {
                word.length > MAX_BUBBLE_CHARS -> {
                    if (buf.isNotBlank()) {
                        chunks += buf.toString().trim()
                        buf.clear()
                    }
                    var rest = word
                    while (rest.length > MAX_BUBBLE_CHARS) {
                        chunks += rest.take(MAX_BUBBLE_CHARS)
                        rest = rest.drop(MAX_BUBBLE_CHARS)
                    }
                    buf.append(rest)
                }
                buf.length + word.length + 1 > MAX_BUBBLE_CHARS && buf.isNotBlank() -> {
                    chunks += buf.toString().trim()
                    buf.clear()
                    buf.append(word)
                }
                else -> {
                    if (buf.isNotBlank()) buf.append(" ")
                    buf.append(word)
                }
            }
        }
        if (buf.isNotBlank()) chunks += buf.toString().trim()
        return chunks
    }
}
