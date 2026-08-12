package com.ev.terminal.harness

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class EvEvent(
    val ts: String,
    val event: String,
    val fields: Map<String, Any> = emptyMap()
) {
    fun toJson(): String {
        val obj = JSONObject()
        obj.put("ts", ts)
        obj.put("event", event)
        fields.forEach { (k, v) -> obj.put(k, v) }
        return obj.toString()
    }

    companion object {
        private val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

        fun now(event: String, vararg fields: Pair<String, Any>): EvEvent =
            EvEvent(fmt.format(Date()), event, fields.toMap())
    }
}

class EventBus {
    private val _events = MutableSharedFlow<EvEvent>(extraBufferCapacity = 512)
    val events: SharedFlow<EvEvent> = _events.asSharedFlow()

    fun emit(event: EvEvent) {
        _events.tryEmit(event)
    }

    fun emit(event: String, vararg fields: Pair<String, Any>) {
        emit(EvEvent.now(event, *fields))
    }
}
