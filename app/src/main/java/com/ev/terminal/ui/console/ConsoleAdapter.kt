package com.ev.terminal.ui.console

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.ev.terminal.R
import com.ev.terminal.harness.EvEvent
import com.ev.terminal.harness.MemorySnapshot
import com.ev.terminal.harness.ModelSnapshot
import com.ev.terminal.harness.TaskRecord

class ConsoleAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<Any>()
    private var tasks: List<TaskRecord> = emptyList()

    private val TYPE_EVENT = 0
    private val TYPE_MEMORY = 1
    private val TYPE_MODEL = 2
    private val TYPE_TOOLS = 3

    fun append(event: EvEvent) {
        items.add(event)
        notifyItemInserted(items.size - 1)
    }

    fun setMemory(snapshot: MemorySnapshot) {
        upsertSnapshot(snapshot, TYPE_MEMORY)
    }

    fun setModel(snapshot: ModelSnapshot) {
        upsertSnapshot(snapshot, TYPE_MODEL)
    }

    fun setTasks(list: List<TaskRecord>) {
        tasks = list
        val old = items.filterIsInstance<TaskRecord>()
        items.removeAll(old)
        items.addAll(0, list.take(8))
        notifyDataSetChanged()
    }

    private fun upsertSnapshot(snapshot: Any, type: Int) {
        val existing = items.indexOfFirst { it::class == snapshot::class }
        if (existing >= 0) {
            items[existing] = snapshot
            notifyItemChanged(existing)
        } else {
            items.add(0, snapshot)
            notifyItemInserted(0)
        }
    }

    fun size(): Int = items.size

    override fun getItemViewType(position: Int): Int {
        val item = items[position]
        return when (item) {
            is EvEvent -> TYPE_EVENT
            is MemorySnapshot -> TYPE_MEMORY
            is ModelSnapshot -> TYPE_MODEL
            is TaskRecord -> TYPE_TOOLS
            else -> TYPE_EVENT
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_MEMORY -> MemoryHolder(inflater.inflate(R.layout.item_console_memory, parent, false))
            TYPE_MODEL -> ModelHolder(inflater.inflate(R.layout.item_console_model, parent, false))
            TYPE_TOOLS -> ToolsHolder(inflater.inflate(R.layout.item_console_tools, parent, false))
            else -> EventHolder(inflater.inflate(R.layout.item_console_event, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is EvEvent -> (holder as EventHolder).bind(item)
            is MemorySnapshot -> (holder as MemoryHolder).bind(item)
            is ModelSnapshot -> (holder as ModelHolder).bind(item)
            is TaskRecord -> (holder as ToolsHolder).bind(item)
        }
    }

    override fun getItemCount(): Int = items.size

    class EventHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val line: TextView = view.findViewById(R.id.console_line)
        fun bind(event: EvEvent) {
            val color = when (event.event) {
                "task_start", "task_end" -> R.color.ev_off_white
                "model_loaded", "model_ready" -> R.color.ev_cyan
                "model_unloaded" -> R.color.ev_gray
                "tool" -> R.color.ev_green
                "ram" -> R.color.ev_amber
                else -> R.color.ev_gray
            }
            line.text = "${event.ts} ${event.event} ${formatFields(event)}"
            line.setTextColor(itemView.context.getColor(color))
        }

        private fun formatFields(event: EvEvent): String {
            if (event.fields.isEmpty()) return ""
            return event.fields.entries.joinToString(" ") { (k, v) -> "$k=$v" }
        }
    }

    class MemoryHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val text: TextView = view.findViewById(R.id.console_memory)
        fun bind(snapshot: MemorySnapshot) {
            text.text = "MEMORY\n\n" +
                "SYSTEM TOTAL     ${snapshot.systemTotalMb} MB\n" +
                "BASELINE         ${snapshot.baselineMb} MB\n" +
                "CURRENT          ${snapshot.currentMb} MB\n" +
                "LAST PEAK        ${snapshot.lastPeakMb} MB\n\n" +
                "MODEL            ${if (snapshot.modelRamMb > 0) "LOADED" else "UNLOADED"}\n" +
                "MODEL RAM        ${snapshot.modelRamMb} MB\n" +
                "CLEANUP DELTA    ${snapshot.cleanupDeltaMb} MB"
        }
    }

    class ModelHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val text: TextView = view.findViewById(R.id.console_model)
        fun bind(snapshot: ModelSnapshot) {
            text.text = "MODEL\n\n" +
                "${snapshot.name}\n" +
                "${snapshot.quant}\n\n" +
                "STATE\n${snapshot.state}\n\n" +
                "LAST LOAD\n${snapshot.lastLoadMs} ms\n\n" +
                "GENERATION\n${"%.1f".format(snapshot.tokPerSec)} tok/s\n\n" +
                "PROMPT\n${snapshot.promptTokens} tok\n\n" +
                "OUTPUT\n${snapshot.outputTokens} tok\n\n" +
                "CONTEXT\n${snapshot.contextUsed} / ${snapshot.contextMax}\n\n" +
                "LAST TASK\n#${snapshot.lastTask.toString().padStart(3, '0')}"
        }
    }

    class ToolsHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val text: TextView = view.findViewById(R.id.console_tools)
        fun bind(task: TaskRecord) {
            text.text = "#${task.id.toString().padStart(3, '0')} ${task.family}\n" +
                "${if (task.status == "SUCCESS") "✓" else "✕"} ${task.status.lowercase()}\n" +
                "${task.durationMs} ms"
        }
    }
}
