package com.ev.terminal.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.widget.TextView
import com.ev.terminal.R
import com.ev.terminal.harness.EVRuntime

class ModelSetupDialog(
    private val context: Context,
    private val runtime: EVRuntime
) : Dialog(context) {

    init {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_model_setup, null)
        setContentView(view)
        window?.setBackgroundDrawable(ColorDrawable(Color.BLACK))
        window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.9f).toInt(),
            (context.resources.displayMetrics.heightPixels * 0.7f).toInt()
        )
        setCancelable(true)

        val info = view.findViewById<TextView>(R.id.setup_info)
        val cancelBtn = view.findViewById<TextView>(R.id.setup_cancel)
        val confirmBtn = view.findViewById<TextView>(R.id.setup_confirm)

        info.text = "EV uses Ollama for local model inference.\n\n" +
            "MODEL\n${runtime.modelSupervisor.modelName}\n\n" +
            "SERVER\n${runtime.settings.modelServerUrl}\n\n" +
            "SETUP\n" +
            "1. Install Ollama on the host machine.\n" +
            "2. Run: ollama pull ${runtime.modelSupervisor.modelName}\n" +
            "3. Keep Ollama running at the server address above.\n\n" +
            "Thinking is disabled for every request.\n\n" +
            "The APK does not download or bundle this model."

        cancelBtn.visibility = android.view.View.GONE
        confirmBtn.text = "CLOSE"
        confirmBtn.setOnClickListener { dismiss() }
        view.findViewById<TextView>(R.id.setup_skip).apply {
            text = "CLOSE"
            setOnClickListener { dismiss() }
        }
        view.findViewById<android.view.View>(R.id.setup_progress).visibility = android.view.View.GONE
        view.findViewById<android.view.View>(R.id.setup_progress_text).visibility = android.view.View.GONE
    }
}
