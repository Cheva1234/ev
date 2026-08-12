package com.ev.terminal

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.ev.terminal.databinding.ActivityMainBinding
import com.ev.terminal.harness.EVRuntime
import com.ev.terminal.ui.ModelSetupDialog
import com.ev.terminal.ui.chat.ChatFragment
import com.ev.terminal.ui.console.ConsoleFragment
import com.ev.terminal.ui.settings.SettingsFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var runtime: EVRuntime

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        runtime = EVRuntime.get(this)

        if (savedInstanceState == null) {
            showFragment(ChatFragment())
        }

        if (!runtime.settings.modelDownloaded) {
            ModelSetupDialog(this, runtime).show()
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_chat -> showFragment(ChatFragment())
                R.id.nav_console -> showFragment(ConsoleFragment())
                R.id.nav_settings -> showFragment(SettingsFragment())
            }
            true
        }
    }

    private fun showFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    override fun onDestroy() {
        super.onDestroy()
        runtime.shutdown()
    }
}
