package com.ev.terminal.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnswerSplitterTest {

    @Test
    fun `short answer stays a single bubble`() {
        assertEquals(listOf("Hello there."), AnswerSplitter.split("Hello there."))
    }

    @Test
    fun `blank input produces no bubbles`() {
        assertTrue(AnswerSplitter.split("").isEmpty())
        assertTrue(AnswerSplitter.split("   \n  ").isEmpty())
    }

    @Test
    fun `paragraphs become separate bubbles`() {
        val text = "First paragraph.\n\nSecond paragraph.\n\nThird paragraph."
        assertEquals(3, AnswerSplitter.split(text).size)
    }

    @Test
    fun `long paragraph is split by lines`() {
        val line = "x".repeat(300)
        val text = (1..6).joinToString("\n") { line }
        val bubbles = AnswerSplitter.split(text)
        assertTrue(bubbles.size >= 2)
        assertTrue(bubbles.all { it.length <= AnswerSplitter.MAX_BUBBLE_CHARS })
    }

    @Test
    fun `single over-long line is word-chunked`() {
        val text = (1..400).joinToString(" ") { "word$it" }
        val bubbles = AnswerSplitter.split(text)
        assertTrue(bubbles.size >= 2)
        assertTrue(bubbles.all { it.length <= AnswerSplitter.MAX_BUBBLE_CHARS })
    }

    @Test
    fun `content is preserved across bubbles`() {
        val words = (1..200).map { "word$it" }
        val text = "Alpha.\n\n" + words.joinToString(" ") + "\n\nOmega."
        val joined = AnswerSplitter.split(text).joinToString(" ")
        words.forEach { assertTrue(joined.contains(it)) }
        assertTrue(joined.contains("Alpha."))
        assertTrue(joined.contains("Omega."))
    }
}
