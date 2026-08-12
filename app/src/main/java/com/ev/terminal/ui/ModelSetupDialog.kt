package com.ev.terminal.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ProgressBar
import android.widget.TextView
import com.ev.terminal.R
import com.ev.terminal.harness.EVRuntime
import com.ev.terminal.model.GgufDownloadProgress
import com.ev.terminal.model.ModelState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ModelSetupDialog(
    private val context: Context,
    private val runtime: EVRuntime
) : Dialog(context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var dismissed = false

    private lateinit var info: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var progressText: TextView
    private lateinit var skipBtn: TextView
    private lateinit var cancelBtn: TextView
    private lateinit var confirmBtn: TextView

    init {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_model_setup, null)
        setContentView(view)
        window?.setBackgroundDrawable(ColorDrawable(Color.BLACK))
        window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.9f).toInt(),
            (context.resources.displayMetrics.heightPixels * 0.7f).toInt()
        )
        setCancelable(false)

        info = view.findViewById(R.id.setup_info)
        progressBar = view.findViewById(R.id.setup_progress)
        progressText = view.findViewById(R.id.setup_progress_text)
        skipBtn = view.findViewById(R.id.setup_skip)
        cancelBtn = view.findViewById(R.id.setup_cancel)
        confirmBtn = view.findViewById(R.id.setup_confirm)

        val supervisor = runtime.modelSupervisor
        val sizeGb = String.format("%.2f", supervisor.modelSizeBytes / 1e9)
        val freeMb = supervisor.freeSpaceBytes() / (1024 * 1024)
        val freeGb = String.format("%.2f", freeMb / 1024.0)

        info.text = "EV needs the local model to answer requests that require reasoning.\n\n" +
            "MODEL\n${supervisor.modelName}\n\n" +
            "SIZE\n$sizeGb GB\n\n" +
            "FREE SPACE\n$freeGb GB available\n\n" +
            "BACKEND\nOn-device (llama.cpp)\n\n" +
            "The model runs entirely on this phone. No server needed.\n\n" +
            "Download now?"

        skipBtn.setOnClickListener { dismiss() }
        cancelBtn.setOnClickListener {
            supervisor.cancelDownload()
            dismiss()
        }
        confirmBtn.setOnClickListener {
            confirmBtn.isEnabled = false
            skipBtn.isEnabled = false
            cancelBtn.visibility = android.view.View.VISIBLE
            progressBar.visibility = android.view.View.VISIBLE
            progressText.visibility = android.view.View.VISIBLE
            confirmBtn.text = "DOWNLOADING"
            supervisor.startDownload()
        }

        scope.launch {
            supervisor.progress.collect { p ->
                if (p != null) updateProgress(p)
            }
        }
        scope.launch {
            supervisor.state.collect { state ->
                if (state == ModelState.READY) {
                    runtime.settings.modelDownloaded = true
                    dismiss()
                } else if (state == ModelState.ERROR) {
                    progressText.text = "ERROR: ${supervisor.error.value ?: "download failed"}"
                    confirmBtn.isEnabled = true
                    skipBtn.isEnabled = true
                    confirmBtn.text = "RETRY"
                }
            }
        }
    }

    private fun updateProgress(p: GgufDownloadProgress) {
        progressBar.progress = p.percent.toInt()
        val doneMb = p.downloadedBytes / (1024 * 1024)
        val totalMb = p.totalBytes / (1024 * 1024)
        progressText.text = if (p.percent >= 100.0) {
            "COMPLETE"
        } else {
            "downloading  $doneMb / $totalMb MB  (${"%.1f".format(p.percent)}%)"
        }
    }

    override fun dismiss() {
        dismissed = true
        scope.cancel()
        super.dismiss()
    }
}
