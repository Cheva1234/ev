package com.ev.terminal.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import com.ev.terminal.R
import com.ev.terminal.harness.EVRuntime
import com.ev.terminal.model.ModelState
import com.ev.terminal.model.formatBytes
import com.ev.terminal.model.MODEL_PACKAGE_SIZE_BYTES
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class ModelSetupDialog(
    context: Context,
    private val runtime: EVRuntime
) : Dialog(context) {

    private val dialogScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val info: TextView
    private val progress: ProgressBar
    private val progressText: TextView
    private val skipBtn: TextView
    private val cancelBtn: TextView
    private val confirmBtn: TextView

    init {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_model_setup, null)
        setContentView(view)
        window?.setBackgroundDrawable(ColorDrawable(Color.BLACK))
        window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.9f).toInt(),
            (context.resources.displayMetrics.heightPixels * 0.7f).toInt()
        )
        setCancelable(true)

        info = view.findViewById(R.id.setup_info)
        progress = view.findViewById(R.id.setup_progress)
        progressText = view.findViewById(R.id.setup_progress_text)
        skipBtn = view.findViewById(R.id.setup_skip)
        cancelBtn = view.findViewById(R.id.setup_cancel)
        confirmBtn = view.findViewById(R.id.setup_confirm)

        skipBtn.text = "CLOSE"
        skipBtn.setOnClickListener { dismiss() }
        cancelBtn.setOnClickListener { runtime.modelSupervisor.cancelDownload() }
        confirmBtn.setOnClickListener {
            when (runtime.modelSupervisor.state.value) {
                ModelState.READY -> dismiss()
                ModelState.DOWNLOADING -> Unit
                else -> runtime.modelSupervisor.startDownload()
            }
        }

        dialogScope.launch {
            runtime.modelSupervisor.state.collect { render(it) }
        }
        dialogScope.launch {
            runtime.modelSupervisor.progress.collect {
                render(runtime.modelSupervisor.state.value)
            }
        }
        render(runtime.modelSupervisor.state.value)
    }

    private fun render(state: ModelState) {
        val supervisor = runtime.modelSupervisor
        val current = supervisor.progress.value
        val downloaded = current?.downloadedBytes ?: supervisor.partialDownloadBytes()

        info.text = when (state) {
            ModelState.NOT_INSTALLED -> baseInfo() +
                "\n\nMODEL STATUS\nNot downloaded\n\n" +
                "Press DOWNLOAD to install the model inside the app. Interrupted downloads can resume."
            ModelState.DOWNLOADING -> baseInfo() +
                "\n\nMODEL STATUS\nDownloading\n\n" +
                "You can close this window. The partial package will remain available for resume."
            ModelState.ERROR -> baseInfo() +
                "\n\nMODEL STATUS\nDownload failed\n\n" +
                (supervisor.error.value ?: "Try again when the network is available.")
            else -> baseInfo() +
                "\n\nMODEL STATUS\nReady\n\n" +
                "The model is installed in app-private storage and runs on-device."
        }

        val showProgress = state == ModelState.DOWNLOADING
        progress.visibility = if (showProgress) View.VISIBLE else View.GONE
        progressText.visibility = if (showProgress) View.VISIBLE else View.GONE
        progress.progress = current?.percent ?: 0
        progressText.text = "${formatBytes(downloaded)} / ${formatBytes(MODEL_PACKAGE_SIZE_BYTES)}"

        cancelBtn.visibility = if (showProgress) View.VISIBLE else View.GONE
        confirmBtn.isEnabled = !showProgress
        confirmBtn.text = when (state) {
            ModelState.READY -> "CLOSE"
            ModelState.ERROR -> "RETRY"
            ModelState.DOWNLOADING -> "DOWNLOADING"
            else -> "DOWNLOAD"
        }
    }

    private fun baseInfo(): String =
        "EV MODEL PACKAGE\n\n" +
            "MODEL\n${runtime.modelSupervisor.modelName}\n\n" +
            "PACKAGE SIZE\n${formatBytes(MODEL_PACKAGE_SIZE_BYTES)}\n\n" +
            "LICENSE\nApache-2.0\n\n" +
            "BACKEND\nllama.cpp (on-device)\n\n" +
            "STORAGE\nPrivate app storage"

    override fun dismiss() {
        dialogScope.cancel()
        super.dismiss()
    }
}
