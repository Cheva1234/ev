package com.ev.terminal.storage

import android.content.Context
import android.content.SharedPreferences

class SettingsStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("ev_settings", Context.MODE_PRIVATE)

    var fastPath: Boolean
        get() = prefs.getBoolean("fast_path", true)
        set(v) = prefs.edit().putBoolean("fast_path", v).apply()

    var progressiveDisclosure: Boolean
        get() = prefs.getBoolean("progressive_disclosure", true)
        set(v) = prefs.edit().putBoolean("progressive_disclosure", v).apply()

    var autoUnload: Boolean
        get() = prefs.getBoolean("auto_unload", true)
        set(v) = prefs.edit().putBoolean("auto_unload", v).apply()

    var ramCleanupVerification: Boolean
        get() = prefs.getBoolean("ram_cleanup_verification", true)
        set(v) = prefs.edit().putBoolean("ram_cleanup_verification", v).apply()

    var taskTimeoutSec: Int
        get() = prefs.getInt("task_timeout_sec", 60)
        set(v) = prefs.edit().putInt("task_timeout_sec", v).apply()

    var contextSize: Int
        get() = prefs.getInt("context_size", 4096)
        set(v) = prefs.edit().putInt("context_size", v).apply()

    var temperature: Float
        get() = prefs.getFloat("temperature", 0.1f)
        set(v) = prefs.edit().putFloat("temperature", v).apply()

    var maxOutputTokens: Int
        get() = prefs.getInt("max_output_tokens", 128)
        set(v) = prefs.edit().putInt("max_output_tokens", v).apply()

    var modelServerUrl: String
        get() = prefs.getString("model_server_url", "http://10.0.2.2:11434") ?: "http://10.0.2.2:11434"
        set(v) = prefs.edit().putString("model_server_url", v).apply()

    var modelDownloaded: Boolean
        get() = prefs.getBoolean("model_downloaded", false)
        set(v) = prefs.edit().putBoolean("model_downloaded", v).apply()

    var toolMath: Boolean
        get() = prefs.getBoolean("tool_math", true)
        set(v) = prefs.edit().putBoolean("tool_math", v).apply()

    var toolTime: Boolean
        get() = prefs.getBoolean("tool_time", true)
        set(v) = prefs.edit().putBoolean("tool_time", v).apply()

    var toolWeather: Boolean
        get() = prefs.getBoolean("tool_weather", true)
        set(v) = prefs.edit().putBoolean("tool_weather", v).apply()

    var toolWeb: Boolean
        get() = prefs.getBoolean("tool_web", true)
        set(v) = prefs.edit().putBoolean("tool_web", v).apply()

    var toolMail: Boolean
        get() = prefs.getBoolean("tool_mail", true)
        set(v) = prefs.edit().putBoolean("tool_mail", v).apply()

    var toolLocation: Boolean
        get() = prefs.getBoolean("tool_location", true)
        set(v) = prefs.edit().putBoolean("tool_location", v).apply()

    var verboseLogs: Boolean
        get() = prefs.getBoolean("verbose_logs", true)
        set(v) = prefs.edit().putBoolean("verbose_logs", v).apply()

    var showEvcl: Boolean
        get() = prefs.getBoolean("show_evcl", true)
        set(v) = prefs.edit().putBoolean("show_evcl", v).apply()

    var showToolResults: Boolean
        get() = prefs.getBoolean("show_tool_results", false)
        set(v) = prefs.edit().putBoolean("show_tool_results", v).apply()

    var showPromptTokens: Boolean
        get() = prefs.getBoolean("show_prompt_tokens", true)
        set(v) = prefs.edit().putBoolean("show_prompt_tokens", v).apply()

    var showRamDelta: Boolean
        get() = prefs.getBoolean("show_ram_delta", true)
        set(v) = prefs.edit().putBoolean("show_ram_delta", v).apply()

    var rawModelOutput: Boolean
        get() = prefs.getBoolean("raw_model_output", false)
        set(v) = prefs.edit().putBoolean("raw_model_output", v).apply()

    fun resetAll() {
        prefs.edit().clear().apply()
    }
}
