package com.ev.terminal.storage

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ChatEntry(
    val role: String,
    val text: String,
    val ts: String,
    val meta: Map<String, String> = emptyMap()
) {
    fun toJson(): String {
        val obj = JSONObject()
        obj.put("role", role)
        obj.put("text", text)
        obj.put("ts", ts)
        meta.forEach { (k, v) -> obj.put(k, v) }
        return obj.toString()
    }

    companion object {
        fun fromJson(line: String): ChatEntry? = try {
            val obj = JSONObject(line)
            val meta = mutableMapOf<String, String>()
            obj.keys().forEach { k ->
                if (k !in setOf("role", "text", "ts")) {
                    meta[k] = obj.optString(k)
                }
            }
            ChatEntry(
                role = obj.optString("role"),
                text = obj.optString("text"),
                ts = obj.optString("ts"),
                meta = meta
            )
        } catch (e: Exception) {
            null
        }
    }
}

class SessionStore(context: Context) {
    private val root = File(context.filesDir, ".ev")
    private val sessionDir = File(root, "session")
    private val sessionsDir = File(root, "sessions")
    private val currentFile = File(sessionDir, "current.jsonl")

    init {
        sessionDir.mkdirs()
        sessionsDir.mkdirs()
        if (!currentFile.exists()) {
            currentFile.createNewFile()
        }
    }

    fun append(entry: ChatEntry) {
        currentFile.appendText(entry.toJson() + "\n")
    }

    fun loadCurrent(): List<ChatEntry> {
        if (!currentFile.exists()) return emptyList()
        return currentFile.readLines().mapNotNull { ChatEntry.fromJson(it) }
    }

    fun archiveCurrent() {
        if (!currentFile.exists() || currentFile.length() == 0L) return
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val archive = File(sessionsDir, "archived-session-$stamp.jsonl")
        currentFile.copyTo(archive, overwrite = true)
        currentFile.writeText("")
    }

    fun clearCurrent() {
        currentFile.writeText("")
    }
}
