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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ChatFragment : Fragment() {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!

    private lateinit var runtime: EVRuntime
    private lateinit var adapter: ChatAdapter
    private lateinit var suggestionAdapter: SuggestionAdapter

    private val entries = mutableListOf<ChatUiEntry>()
    private var sessionId = 1
    private var taskRunning = false
    private var liveEntryIndex: Int? = null
    private val liveRaw = StringBuilder()
    private var lastLiveUpdateMs = 0L
    private var spinnerJob: Job? = null
    private var spinnerFrame = 0
    private var liveLabel = "THINKING"
    private val spinnerFrames = listOf("|", "/", "—", "\\")

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
        if (taskRunning) return
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
        val base = command.substringBefore(' ').trim()
        val args = command.substringAfter(' ', "").trim()
        when (base) {
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
                when (args) {
                    "load" -> {
                        val supervisor = runtime.modelSupervisor
                        if (supervisor.isInstalled()) {
                            appendEv("Model already installed.")
                        } else {
                            appendEv("Opening model setup...")
                            com.ev.terminal.ui.ModelSetupDialog(requireContext(), runtime).show()
                        }
                    }
                    else -> {
                        val supervisor = runtime.modelSupervisor
                        val state = supervisor.state.value
                        val ctx = requireContext()
                        val libDir = ctx.applicationInfo.nativeLibraryDir
                        val cli = File(libDir, "libllama-cli.so")
                        val model = File(ctx.filesDir, ".ev/models/lfm2.5-2.6b-q4_k_m.gguf")
                        appendEv("MODEL\n\n" +
                            "STATE\n$state\n\n" +
                            "GGUF FILE\n${if (model.exists()) "present (${model.length() / 1048576} MB)" else "missing"}\n\n" +
                            "LLAMA-CLI\n${if (cli.exists()) "present ($libDir)" else "missing ($libDir)"}\n\n" +
                            "ARCH\n${android.os.Build.SUPPORTED_ABIS.joinToString()}\n\n" +
                            "To install the model, run: /model load")
                    }
                }
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
        taskRunning = true
        binding.sendBtn.isEnabled = false
        lifecycleScope.launch {
            try {
                val outcome: TaskOutcome? = if (text.trim().startsWith("@")) {
                    runtime.taskManager.runEvcl(text)
                } else {
                    runtime.fastPath.tryResolve(text)?.let { result ->
                        TaskOutcome(
                            taskId = runtime.state.nextTask(),
                            family = result.family,
                            result = result,
                            durationMs = result.durationMs
                        )
                    } ?: runModelTask(text)
                }
                when {
                    outcome != null -> {
                        appendTool(outcome)
                        if (!outcome.responseStreamed) appendEv(evAnswer(outcome))
                    }
                    else -> {
                        appendEv("LFM2.5 is not installed. This request needs reasoning.\n\nInstall it with /model load, or try a direct tool command, e.g. @math 84*9.81, or /help.")
                        com.ev.terminal.ui.ModelSetupDialog(requireContext(), runtime).show()
                    }
                }
            } catch (e: Exception) {
                val errorMsg = e.message ?: e.toString()
                val errorText = "MODEL ERROR\n\n$errorMsg\n\nCheck CONSOLE for details. Run /model for status."
                if (liveEntryIndex != null) finishLiveStream(errorText) else appendEv(errorText)
            } finally {
                taskRunning = false
                binding.sendBtn.isEnabled = true
            }
        }
    }

    private suspend fun streamChunk(chunk: String) {
        withContext(Dispatchers.Main.immediate) {
            appendLiveChunk(chunk)
        }
    }

    private fun startLiveStream() {
        liveRaw.clear()
        lastLiveUpdateMs = 0L
        liveLabel = "THINKING"
        spinnerFrame = 0
        spinnerJob?.cancel()
        val entry = ChatUiEntry("EV", "| THINKING…")
        entries.add(entry)
        liveEntryIndex = entries.lastIndex
        adapter.append(entry)
        scrollToBottom()
        spinnerJob = lifecycleScope.launch {
            while (isActive && liveEntryIndex != null) {
                updateLiveEntry(livePreview())
                spinnerFrame = (spinnerFrame + 1) % spinnerFrames.size
                delay(180L)
            }
        }
    }

    private fun resetLiveStream(label: String) {
        liveRaw.clear()
        lastLiveUpdateMs = 0L
        liveLabel = label.removeSuffix("…").uppercase()
        spinnerFrame = 0
        updateLiveEntry(label)
    }

    private fun appendLiveChunk(chunk: String) {
        if (liveEntryIndex == null) return
        liveRaw.append(chunk)
        val now = System.currentTimeMillis()
        if (now - lastLiveUpdateMs < 80L && !chunk.contains('\n')) return
        lastLiveUpdateMs = now
        updateLiveEntry(livePreview())
    }

    private fun livePreview(): String {
        val raw = liveRaw.toString()
        val indicator = spinnerFrames[spinnerFrame]
        val endThinking = raw.lastIndexOf("[End thinking]")
        if (endThinking >= 0) {
            val answer = raw.substring(endThinking + "[End thinking]".length)
                .substringBefore("[ Prompt:")
                .substringBefore("Exiting...")
                .trim()
            if (answer.isNotEmpty()) return "$indicator $liveLabel…\n\n$answer"
        }

        val startThinking = raw.lastIndexOf("[Start thinking]")
        if (startThinking >= 0) {
            val thinking = raw.substring(startThinking + "[Start thinking]".length).trim()
            return "$indicator THINKING…\n\n${thinking.takeLast(4096)}".trim()
        }
        return "$indicator $liveLabel…\n\n${raw.takeLast(4096)}".trim()
    }

    private fun updateLiveEntry(text: String) {
        val index = liveEntryIndex ?: return
        val updated = entries[index].copy(text = text.ifBlank { "THINKING…" })
        entries[index] = updated
        adapter.update(index, updated)
        scrollToBottom()
    }

    private fun finishLiveStream(text: String) {
        val index = liveEntryIndex ?: return
        spinnerJob?.cancel()
        spinnerJob = null
        val finalText = text.trim().ifBlank { "(model returned no visible text)" }
        val updated = entries[index].copy(text = finalText)
        entries[index] = updated
        adapter.update(index, updated)
        runtime.sessionStore.append(ChatEntry("EV", finalText, ts = now()))
        liveEntryIndex = null
        liveRaw.clear()
        scrollToBottom()
    }

    private suspend fun runModelTask(text: String): TaskOutcome? {
        val supervisor = runtime.modelSupervisor
        if (!supervisor.isInstalled()) return null

        withContext(Dispatchers.Main.immediate) { startLiveStream() }
        val stream: suspend (String) -> Unit = { chunk -> streamChunk(chunk) }

        val commandSystem = "Classify the user request. " +
            "Reply with exactly one EVCL command if a tool can help, otherwise reply with exactly NONE. " +
            "EVCL commands: @math <expr>, @time now, @weather <op> \"<loc>\", " +
            "@web search \"<query>\", @mail latest, @loc current. " +
            "Output one line only. Do not explain, plan, count words, or show reasoning.\n\n" +
            "Examples:\n" +
            "User: differentiate x^2*sin(x)\nEVCL: @math diff(x^2*sin(x),x)\n" +
            "User: what is 84*9.81\nEVCL: @math 84*9.81\n" +
            "User: what time is it\nEVCL: @time now\n" +
            "User: weather in Bangkok\nEVCL: @weather current \"Bangkok\"\n" +
            "User: search for Formula Student rules\nEVCL: @web search \"Formula Student rules\"\n" +
            "User: check my email\nEVCL: @mail latest\n" +
            "User: hello\nEVCL: NONE"

        val answerSystem = "Answer the user's request directly in one or two short sentences. " +
            "Do not show thinking, planning, analysis, or word counts. No commands."

        val commandText = extractEvcl(
            supervisor.runTask(commandSystem, "User: $text\nEVCL:", maxTokens = 200, onChunk = stream).text
        )
        withContext(Dispatchers.Main.immediate) { resetLiveStream("ANSWERING…") }
        if (commandText != null && commandText.startsWith("@")) {
            val result = runtime.taskManager.runEvcl(commandText)
            val answerPrompt = "Tool result:\n${result.result.detail}\n\n" +
                "Answer the user's request in under 30 words using the result."
            val answer = stripThink(
                supervisor.runTask(answerSystem, answerPrompt, maxTokens = 128, onChunk = stream).text
            )
            withContext(Dispatchers.Main.immediate) { finishLiveStream(answer) }
            return TaskOutcome(
                taskId = result.taskId,
                family = result.family,
                result = result.result.copy(summary = answer),
                durationMs = result.durationMs,
                responseStreamed = true
            )
        }
        val answer = stripThink(
            supervisor.runTask(answerSystem, "User: $text\nEV:", maxTokens = 128, onChunk = stream).text
        )
        withContext(Dispatchers.Main.immediate) { finishLiveStream(answer) }
        return TaskOutcome(
            taskId = runtime.state.nextTask(),
            family = "LFM",
            result = com.ev.terminal.tools.ToolResult(
                "LFM",
                com.ev.terminal.tools.ToolStatus.SUCCESS,
                answer,
                "LFM_RESULT\nstatus=SUCCESS\nvalue=$answer"
            ),
            durationMs = 0,
            responseStreamed = true
        )
    }

    private fun stripThink(text: String): String {
        var result = text
        if (result.contains(" response")) {
            result = result.replace(Regex(".*? response", RegexOption.DOT_MATCHES_ALL), "")
        }
        return result.replace(Regex("<thinking>.*?</thinking>", RegexOption.DOT_MATCHES_ALL), "")
            .trim()
    }

    private fun extractEvcl(text: String): String? {
        val cleaned = stripThink(text)
        val segment = if (cleaned.contains(" response")) {
            cleaned.substringAfterLast(" response")
        } else {
            cleaned
        }
        val match = Regex("@\\w+[^\\n]*").find(segment)
        return match?.groupValues?.get(0)?.trim()
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
