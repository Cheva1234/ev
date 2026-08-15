package com.ev.terminal.ui.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LatexHtmlBuilderTest {

    @Test
    fun `builds display and inline math for offline KaTeX`() {
        val html = LatexHtmlBuilder.build("Answer $" + "x^2$ and $$\\frac{1}{2}$$")

        assertTrue(html.contains("class=\"math math-inline\""))
        assertTrue(html.contains("class=\"math math-block\""))
        assertTrue(html.contains("katex.render"))
        assertTrue(html.contains("\\frac{1}{2}"))
    }

    @Test
    fun `escapes non-math content before putting it in the web document`() {
        val html = LatexHtmlBuilder.build("<script>alert('x')</script>")

        assertFalse(html.contains("<script>alert('x')</script>"))
        assertTrue(html.contains("&lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt;"))
    }
}
