package com.ev.terminal.ui.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.ev.terminal.R
import com.ev.terminal.databinding.FragmentChatBinding
import com.ev.terminal.harness.EVRuntime
import com.ev.terminal.harness.RuntimeState
import com.ev.terminal.harness.TaskOutcome
import com.ev.terminal.storage.ChatEntry
import com.ev.terminal.tools.ToolStatus
import kotlinx.coroutines.launch

class ChatFragment : Fragment() {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!

    private lateinit var runtime: EVRuntime
    private lateinit var adapter: ChatAdapter
    private lateinit var suggestionAdapter: SuggestionAdapter

    private val entries = mutableListOf<ChatUiEntry>()
    private var sessionId = 1

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        runtime = EVRuntime.get(requireContext())

        adapter = ChatAdapter { position -> toggleToolTrace(position) }
        binding.chatList.layoutManager = LinearLayoutManager(requireContext())
        binding.chatList.adapter = adapter

        suggestionAdapter = SuggestionAdapter { command ->
            binding.input.setText(command)
            binding.input.setSelection(command.length)
            hideSuggestions()
        }
        binding.suggestionList.layoutManager = LinearLayoutManager(requireContext())
        binding.suggestionList.adapter = suggestionAdapter

        sessionId = runtime.state.sessionId.value
        updateHeader()

        loadSession()
        observeState()

        binding.input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                submit()
                true
            } else false
        }
        binding.sendBtn.setOnClickListener { submit() }

        binding.input.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                updateSuggestions(s?.toString().orEmpty())
            }
        })
    }

    private fun loadSession() {
        entries.clear()
        runtime.sessionStore.loadCurrent().forEach { entry ->
            when (entry.role) {
                "USER" -> entries.add(ChatUiEntry("USER", entry.text))
                "EV" -> entries.add(ChatUiEntry("EV", entry.text))
                "SYSTEM" -> entries.add(ChatUiEntry("SYSTEM", entry.text))
                "TOOL" -> entries.add(
                    ChatUiEntry(
                        "TOOL",
                        entry.text,
                        family = entry.meta["family"].orEmpty(),
                        durationMs = entry.meta["duration_ms"]?.toLongOrNull() ?: 0,
                        status = entry.meta["status"].orEmpty()
                    )
                )
            }
        }
        adapter.submit(entries)
        scrollToBottom()
    }

    private fun observeState() {
        lifecycleScope.launch {
            runtime.state.sessionId.collect { id ->
                sessionId = id
                updateHeader()
            }
        }
        lifecycleScope.launch {
            runtime.state.runtimeState.collect { state ->
                binding.headerState.text = when (state) {
                    RuntimeState.IDLE -> "IDLE"
                    RuntimeState.AI -> "LFM ACTIVE"
                    RuntimeState.TOOL -> "TOOL"
                    RuntimeState.ERROR -> "ERROR"
                }
                binding.headerState.setTextColor(
                    requireContext().getColor(
                        when (state) {
                            RuntimeState.IDLE -> R.color.ev_green
                            RuntimeState.AI -> R.color.ev_cyan
                            RuntimeState.TOOL -> R.color.ev_amber
                            RuntimeState.ERROR -> R.color.ev_red
                        }
                    )
                )
            }
        }
    }

    private fun updateHeader() {
        binding.headerTitle.text = "EV / SESSION ${sessionId.toString().padStart(2, '0')}"
    }

    private fun submit() {
        val text = binding.input.text.toString().trim()
        if (text.isEmpty()) return
        binding.input.setText("")
        hideSuggestions()

        if (text.startsWith("/")) {
            handleSlashCommand(text)
        } else {
            handleUserTask(text)
        }
    }

    private fun handleSlashCommand(command: String) {
        appendSystem("> $command")
        when (command) {
            "/new" -> {
                runtime.newSession()
                entries.clear()
                adapter.submit(entries)
                appendSystem("[session reset]")
            }
            "/clear" -> {
                entries.clear()
                adapter.submit(entries)
                runtime.sessionStore.clearCurrent()
                appendSystem("[view cleared]")
            }
            "/help" -> {
                appendEv(helpText())
            }
            "/status" -> {
                val state = runtime.state.runtimeState.value
                appendEv("STATUS\n\nstate=$state\nsession=${sessionId}\ntasks=${runtime.state.taskCounter.value}")
            }
            "/tools" -> {
                appendEv(toolsText())
            }
            "/model" -> {
                val m = runtime.state.model.value
                appendEv("MODEL\n\n${m.name}\n${m.quant}\n\nSTATE\n${m.state}\n\nCONTEXT\n${m.contextUsed} / ${m.contextMax}")
            }
            "/memory" -> {
                val mem = runtime.state.memory.value
                appendEv("MEMORY\n\nSYSTEM TOTAL     ${mem.systemTotalMb} MB\nCURRENT          ${mem.currentMb} MB\nMODEL            ${mem.modelRamMb} MB")
            }
            else -> {
                appendEv("Unknown command: $command\nType /help for the guide.")
            }
        }
    }

    private fun handleUserTask(text: String) {
        appendUser(text)
        lifecycleScope.launch {
            val outcome: TaskOutcome? = try {
                runtime.taskManager.runEvcl(text)
            } catch (e: Exception) {
                null
            }
            if (outcome == null) {
                appendEv("No tool matched that request. Try /tools or /help.")
            } else {
                appendTool(outcome)
                appendEv(evAnswer(outcome))
            }
        }
    }

    private fun evAnswer(outcome: TaskOutcome): String {
        val r = outcome.result
        return when (r.status) {
            ToolStatus.SUCCESS -> r.summary
            ToolStatus.ERROR -> "Error: ${r.summary}"
            ToolStatus.PARTIAL -> "Partial: ${r.summary}"
            ToolStatus.PERMISSION_REQUIRED -> r.summary
            ToolStatus.AMBIGUOUS -> r.summary
            ToolStatus.NOT_FOUND -> r.summary
        }
    }

    private fun appendUser(text: String) {
        val entry = ChatUiEntry("USER", text)
        entries.add(entry)
        adapter.append(entry)
        runtime.sessionStore.append(ChatEntry("USER", text, ts = now()))
        scrollToBottom()
    }

    private fun appendEv(text: String) {
        val entry = ChatUiEntry("EV", text)
        entries.add(entry)
        adapter.append(entry)
        runtime.sessionStore.append(ChatEntry("EV", text, ts = now()))
        scrollToBottom()
    }

    private fun appendSystem(text: String) {
        val entry = ChatUiEntry("SYSTEM", text)
        entries.add(entry)
        adapter.append(entry)
        runtime.sessionStore.append(ChatEntry("SYSTEM", text, ts = now()))
        scrollToBottom()
    }

    private fun appendTool(outcome: TaskOutcome) {
        val entry = ChatUiEntry(
            "TOOL",
            outcome.result.detail.ifEmpty { outcome.result.summary },
            family = outcome.family,
            durationMs = outcome.durationMs,
            status = outcome.result.status.name
        )
        entries.add(entry)
        adapter.append(entry)
        runtime.sessionStore.append(
            ChatEntry(
                "TOOL",
                entry.text,
                ts = now(),
                meta = mapOf(
                    "family" to outcome.family,
                    "duration_ms" to outcome.durationMs.toString(),
                    "status" to outcome.result.status.name
                )
            )
        )
        scrollToBottom()
    }

    private fun toggleToolTrace(position: Int) {
        val current = entries[position]
        if (current.role != "TOOL") return
        val updated = current.copy(expanded = !current.expanded)
        entries[position] = updated
        adapter.update(position, updated)
    }

    private fun updateSuggestions(text: String) {
        if (text.startsWith("/")) {
            val filtered = SlashCommands.all.filter { it.command.startsWith(text) }
            if (filtered.isNotEmpty()) {
                suggestionAdapter.submit(filtered)
                binding.suggestionContainer.isVisible = true
                return
            }
        }
        hideSuggestions()
    }

    private fun hideSuggestions() {
        binding.suggestionContainer.isVisible = false
    }

    private fun scrollToBottom() {
        binding.chatList.post {
            binding.chatList.scrollToPosition(adapter.size() - 1)
        }
    }

    private fun now(): String =
        java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())

    private fun helpText(): String =
        "EV GUIDE\n\nQUICK START\n\n> what's the weather tomorrow?\n\n> differentiate x² sin(x)\n\n> check latest email\n\n> search web for Formula Student rules\n\nCOMMANDS\n\n/new\n/status\n/tools\n/model\n/memory\n/help\n/clear"

    private fun toolsText(): String =
        "TOOLS\n\nMATH        READY\nTIME        READY\nWEATHER     READY\nWEB         READY\nMAIL        READY\nLOCATION    READY\n\nFILES       DEFERRED"

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
