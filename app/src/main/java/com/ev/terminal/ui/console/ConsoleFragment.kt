package com.ev.terminal.ui.console

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.ev.terminal.R
import com.ev.terminal.databinding.FragmentConsoleBinding
import com.ev.terminal.harness.EVRuntime
import com.ev.terminal.harness.TaskRecord
import kotlinx.coroutines.launch

class ConsoleFragment : Fragment() {

    private var _binding: FragmentConsoleBinding? = null
    private val binding get() = _binding!!

    private lateinit var runtime: EVRuntime
    private lateinit var adapter: ConsoleAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentConsoleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        runtime = EVRuntime.get(requireContext())

        adapter = ConsoleAdapter()
        binding.consoleList.layoutManager = LinearLayoutManager(requireContext())
        binding.consoleList.adapter = adapter

        lifecycleScope.launch {
            runtime.eventBus.events.collect { event ->
                adapter.append(event)
                binding.consoleList.scrollToPosition(adapter.size() - 1)
            }
        }
        lifecycleScope.launch {
            runtime.state.tasks.collect { tasks ->
                adapter.setTasks(tasks)
            }
        }
        lifecycleScope.launch {
            runtime.state.memory.collect { mem ->
                adapter.setMemory(mem)
            }
        }
        lifecycleScope.launch {
            runtime.state.model.collect { model ->
                adapter.setModel(model)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
