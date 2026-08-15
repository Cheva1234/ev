package com.ev.terminal.ui.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.ev.terminal.R

class ChatAdapter(
    private val onToolClick: (Int) -> Unit
) : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    private val entries = mutableListOf<ChatUiEntry>()

    fun submit(list: List<ChatUiEntry>) {
        entries.clear()
        entries.addAll(list)
        notifyDataSetChanged()
    }

    fun append(entry: ChatUiEntry) {
        entries.add(entry)
        notifyItemInserted(entries.size - 1)
    }

    fun update(index: Int, entry: ChatUiEntry) {
        entries[index] = entry
        notifyItemChanged(index)
    }

    fun size(): Int = entries.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_entry, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        holder.bind(entries[position], position)
    }

    override fun getItemCount(): Int = entries.size

    inner class ChatViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val label: TextView = view.findViewById(R.id.entry_label)
        private val body: TextView = view.findViewById(R.id.entry_body)
        private val mathBody: WebView = view.findViewById(R.id.entry_math_body)
        private val trace: TextView = view.findViewById(R.id.entry_trace)

        fun bind(entry: ChatUiEntry, position: Int) {
            when (entry.role) {
                "USER" -> {
                    label.visibility = View.VISIBLE
                    label.text = "USER:"
                    label.setTextColor(itemView.context.getColor(R.color.ev_gray))
                    mathBody.visibility = View.GONE
                    body.text = entry.text
                    body.visibility = View.VISIBLE
                    body.setTextColor(itemView.context.getColor(R.color.ev_off_white))
                    trace.visibility = View.GONE
                }
                "EV" -> {
                    label.visibility = View.VISIBLE
                    label.text = "EV:"
                    label.setTextColor(itemView.context.getColor(R.color.ev_cyan))
                    if (LatexHtmlBuilder.containsMath(entry.text)) {
                        body.visibility = View.GONE
                        mathBody.visibility = View.VISIBLE
                        LatexWebViewRenderer.render(mathBody, entry.text)
                    } else {
                        mathBody.visibility = View.GONE
                        val rendered = MarkdownRenderer.render(entry.text, itemView.context)
                        MathRenderer.renderInline(rendered, itemView.context)
                        MathRenderer.renderBlock(rendered, itemView.context)
                        body.text = rendered
                        body.visibility = View.VISIBLE
                    }
                    body.setTextColor(itemView.context.getColor(R.color.ev_off_white))
                    trace.visibility = View.GONE
                }
                "SYSTEM" -> {
                    label.visibility = View.GONE
                    mathBody.visibility = View.GONE
                    body.visibility = View.VISIBLE
                    body.text = entry.text
                    body.setTextColor(itemView.context.getColor(R.color.ev_gray))
                    trace.visibility = View.GONE
                }
                "TOOL" -> {
                    label.visibility = View.GONE
                    mathBody.visibility = View.GONE
                    body.visibility = View.VISIBLE
                    body.text = if (entry.expanded) {
                        "[ ${entry.family} · ${entry.durationMs} ms ]\n\n" +
                            "command\n${entry.text}\n\n" +
                            "status\n${entry.status}\n\n" +
                            "duration\n${entry.durationMs} ms"
                    } else {
                        "[ ${entry.family} · ${entry.durationMs} ms ]"
                    }
                    body.setTextColor(itemView.context.getColor(R.color.ev_gray))
                    trace.visibility = View.GONE
                    body.setOnClickListener { onToolClick(position) }
                }
            }
        }
    }
}
