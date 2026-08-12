package com.ev.terminal.ui.chat

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import androidx.core.content.ContextCompat
import com.ev.terminal.R
import java.util.regex.Pattern

object MathRenderer {

    private val inlinePattern = Pattern.compile("\\$([^$\\n]+?)\\$")
    private val blockPattern = Pattern.compile("\\$\\$([\\s\\S]+?)\\$\\$")

    fun renderInline(text: SpannableStringBuilder, context: android.content.Context) {
        val cyan = ContextCompat.getColor(context, R.color.ev_cyan)
        val matcher = inlinePattern.matcher(text)
        while (matcher.find()) {
            val start = matcher.start()
            val end = matcher.end()
            text.setSpan(ForegroundColorSpan(cyan), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    fun renderBlock(text: SpannableStringBuilder, context: android.content.Context) {
        val green = ContextCompat.getColor(context, R.color.ev_green)
        val matcher = blockPattern.matcher(text)
        while (matcher.find()) {
            val start = matcher.start()
            val end = matcher.end()
            text.setSpan(ForegroundColorSpan(green), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
}
