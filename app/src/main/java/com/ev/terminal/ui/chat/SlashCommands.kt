package com.ev.terminal.ui.chat

data class SlashCommand(
    val command: String,
    val description: String
)

object SlashCommands {
    val all = listOf(
        SlashCommand("/new", "Start new session"),
        SlashCommand("/status", "Show EV status"),
        SlashCommand("/tools", "Show available tools"),
        SlashCommand("/model", "Show model state"),
        SlashCommand("/memory", "Show RAM usage"),
        SlashCommand("/help", "Open guide"),
        SlashCommand("/clear", "Clear visible console/chat view")
    )
}
