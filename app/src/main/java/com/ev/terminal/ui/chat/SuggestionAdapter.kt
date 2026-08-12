package com.ev.terminal.ui.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.ev.terminal.R

class SuggestionAdapter(
    private val onClick: (String) -> Unit
) : RecyclerView.Adapter<SuggestionAdapter.SuggestionViewHolder>() {

    private val items = mutableListOf<SlashCommand>()

    fun submit(list: List<SlashCommand>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SuggestionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_suggestion, parent, false)
        return SuggestionViewHolder(view)
    }

    override fun onBindViewHolder(holder: SuggestionViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class SuggestionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val command: TextView = view.findViewById(R.id.suggestion_command)
        private val description: TextView = view.findViewById(R.id.suggestion_description)

        fun bind(item: SlashCommand) {
            command.text = item.command
            description.text = item.description
            itemView.setOnClickListener { onClick(item.command) }
        }
    }
}
