package com.ev.terminal.ui.chat

import android.graphics.Color
import android.webkit.WebView
import android.webkit.WebViewClient

object LatexWebViewRenderer {

    fun render(webView: WebView, text: String) {
        webView.setBackgroundColor(Color.TRANSPARENT)
        webView.isVerticalScrollBarEnabled = false
        webView.isHorizontalScrollBarEnabled = false
        webView.overScrollMode = WebView.OVER_SCROLL_NEVER
        webView.settings.javaScriptEnabled = true
        webView.settings.allowFileAccess = true
        webView.webViewClient = WebViewClient()
        webView.loadDataWithBaseURL(
            "file:///android_asset/katex/",
            LatexHtmlBuilder.build(text),
            "text/html",
            "UTF-8",
            null
        )
    }
}
