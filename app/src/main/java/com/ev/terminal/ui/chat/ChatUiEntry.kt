package com.ev.terminal.ui.chat

data class ChatUiEntry(
    val role: String,
    val text: String,
    val family: String = "",
    val durationMs: Long = 0,
    val status: String = "",
    val detail: String = "",
    val expanded: Boolean = false
)
