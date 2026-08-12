package com.ev.terminal.ui.chat

import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import androidx.core.content.ContextCompat
import com.ev.terminal.R
import org.commonmark.node.BlockQuote
import org.commonmark.node.Code
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.Heading
import org.commonmark.node.Link
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.Paragraph
import org.commonmark.node.Text
import org.commonmark.parser.Parser

object MarkdownRenderer {

    private val parser: Parser = Parser.builder().build()

    fun render(markdown: String, context: android.content.Context): SpannableStringBuilder {
        val sb = SpannableStringBuilder()
        val document = parser.parse(markdown)
        renderNode(document, sb, context, 0)
        return sb
    }

    private fun renderNode(node: Node, sb: SpannableStringBuilder, context: android.content.Context, depth: Int) {
        when (node) {
            is Heading -> {
                val start = sb.length
                renderChildren(node, sb, context, depth)
                val size = when (node.level) {
                    1 -> 20f
                    2 -> 18f
                    else -> 16f
                }
                sb.setSpan(
                    android.text.style.RelativeSizeSpan(size / 14f),
                    start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                sb.setSpan(
                    StyleSpan(Typeface.BOLD),
                    start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                sb.append("\n")
            }
            is Paragraph -> {
                renderChildren(node, sb, context, depth)
                sb.append("\n")
            }
            is Text -> {
                sb.append(node.literal)
            }
            is Code -> {
                val start = sb.length
                sb.append(node.literal)
                sb.setSpan(
                    ForegroundColorSpan(ContextCompat.getColor(context, R.color.ev_cyan)),
                    start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            is FencedCodeBlock -> {
                val start = sb.length
                sb.append(node.literal)
                sb.setSpan(
                    ForegroundColorSpan(ContextCompat.getColor(context, R.color.ev_green)),
                    start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                sb.append("\n")
            }
            is BlockQuote -> {
                val start = sb.length
                renderChildren(node, sb, context, depth)
                sb.setSpan(
                    ForegroundColorSpan(ContextCompat.getColor(context, R.color.ev_gray)),
                    start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                sb.append("\n")
            }
            is Link -> {
                renderChildren(node, sb, context, depth)
            }
            is ListItem -> {
                sb.append("  • ")
                renderChildren(node, sb, context, depth)
                sb.append("\n")
            }
            else -> {
                renderChildren(node, sb, context, depth)
            }
        }
    }

    private fun renderChildren(node: Node, sb: SpannableStringBuilder, context: android.content.Context, depth: Int) {
        var child = node.firstChild
        while (child != null) {
            renderNode(child, sb, context, depth + 1)
            child = child.next
        }
    }
}
