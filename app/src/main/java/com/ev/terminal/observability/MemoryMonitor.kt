package com.ev.terminal.observability

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class MemorySample(
    val totalMb: Long,
    val usedMb: Long,
    val appPssMb: Long
)

class MemoryMonitor {
    private val _samples = MutableSharedFlow<MemorySample>(extraBufferCapacity = 64)
    val samples: SharedFlow<MemorySample> = _samples.asSharedFlow()

    fun snapshot(context: Context): MemorySample {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        val totalMb = memInfo.totalMem / (1024 * 1024)
        val usedMb = (memInfo.totalMem - memInfo.availMem) / (1024 * 1024)
        val appPssMb = Debug.getPss() / 1024
        return MemorySample(totalMb, usedMb, appPssMb)
    }

    suspend fun sampleLoop(context: Context) {
        while (true) {
            _samples.tryEmit(snapshot(context))
            delay(2000)
        }
    }
}
