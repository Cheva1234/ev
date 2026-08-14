package com.ev.terminal.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.ev.terminal.R
import com.ev.terminal.databinding.FragmentSettingsBinding
import com.ev.terminal.harness.EVRuntime

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var runtime: EVRuntime

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        runtime = EVRuntime.get(requireContext())
        val s = runtime.settings

        binding.valueModel.text = "${runtime.modelSupervisor.modelName}\n\n" +
            "Backend\nllama.cpp (on-device)\n\n" +
            "Storage\nDownloaded separately\n\n" +
            "Context\n${s.contextSize}\n\n" +
            "Temperature\n${s.temperature}\n\n" +
            "Max output\n${s.maxOutputTokens}\n\n" +
            "Lifecycle\nLoad per task"

        binding.valuePrivacy.text = "AI PROCESSING\nOn-device\n\n" +
            "CHAT STORAGE\nLocal\n\n" +
            "TASK LOGS\nLocal\n\n" +
            "WEB\nNetwork only when Web tool is used\n\n" +
            "WEATHER\nNetwork only when Weather tool is used\n\n" +
            "MAIL\nDirect provider access"

        bindSwitch(binding.switchDisclosure, s.progressiveDisclosure) { s.progressiveDisclosure = it }
        bindSwitch(binding.switchAutoUnload, s.autoUnload) { s.autoUnload = it }
        bindSwitch(binding.switchRamVerify, s.ramCleanupVerification) { s.ramCleanupVerification = it }
        bindSwitch(binding.switchToolMath, s.toolMath) { s.toolMath = it }
        bindSwitch(binding.switchToolTime, s.toolTime) { s.toolTime = it }
        bindSwitch(binding.switchToolWeather, s.toolWeather) { s.toolWeather = it }
        bindSwitch(binding.switchToolWeb, s.toolWeb) { s.toolWeb = it }
        bindSwitch(binding.switchToolMail, s.toolMail) { s.toolMail = it }
        bindSwitch(binding.switchToolLocation, s.toolLocation) { s.toolLocation = it }
        bindSwitch(binding.switchVerbose, s.verboseLogs) { s.verboseLogs = it }
        bindSwitch(binding.switchShowEvcl, s.showEvcl) { s.showEvcl = it }
        bindSwitch(binding.switchShowToolResults, s.showToolResults) { s.showToolResults = it }
        bindSwitch(binding.switchShowPromptTokens, s.showPromptTokens) { s.showPromptTokens = it }
        bindSwitch(binding.switchShowRamDelta, s.showRamDelta) { s.showRamDelta = it }
        bindSwitch(binding.switchRawModel, s.rawModelOutput) { s.rawModelOutput = it }

        binding.btnClearSession.setOnClickListener {
            runtime.sessionStore.clearCurrent()
            toast("Session cleared")
        }
        binding.btnDeleteLogs.setOnClickListener {
            runtime.logger.deleteAll()
            toast("Logs deleted")
        }
        binding.btnResetEv.setOnClickListener {
            s.resetAll()
            runtime.sessionStore.clearCurrent()
            runtime.logger.deleteAll()
            toast("EV reset")
        }
    }

    private fun bindSwitch(switch: com.google.android.material.materialswitch.MaterialSwitch, value: Boolean, setter: (Boolean) -> Unit) {
        switch.isChecked = value
        switch.setOnCheckedChangeListener { _, checked -> setter(checked) }
    }

    private fun toast(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
