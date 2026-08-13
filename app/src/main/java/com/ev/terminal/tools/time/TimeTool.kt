package com.ev.terminal.tools.time

import com.ev.terminal.evcl.EvclCommand
import com.ev.terminal.tools.Tool
import com.ev.terminal.tools.ToolResult
import com.ev.terminal.tools.ToolStatus
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class TimeTool : Tool {
    override val family = "TIME"
    override val operations = listOf("now", "zone", "diff", "convert")
    override val usage = "TIME: current time/date or timezone. Examples: @time now, @time Asia/Tokyo, @time convert Asia/Bangkok UTC"

    override suspend fun execute(command: EvclCommand): ToolResult {
        val cmd = command as? EvclCommand.Time ?: return error("bad command")
        return when (cmd.operation) {
            "now" -> {
                val now = LocalDateTime.now()
                val fmt = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.US)
                val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)
                ToolResult(
                    family, ToolStatus.SUCCESS,
                    "${now.format(dateFmt)} ${now.format(fmt)}",
                    "TIME_RESULT\nstatus=SUCCESS\nlocal=${now.format(dateFmt)} ${now.format(fmt)}"
                )
            }
            "zone" -> {
                val zoneName = cmd.args.firstOrNull() ?: return error("missing zone")
                val zone = resolveZone(zoneName) ?: return ToolResult(
                    family, ToolStatus.NOT_FOUND,
                    "unknown timezone: $zoneName",
                    "TIME_RESULT\nstatus=NOT_FOUND\nzone=$zoneName"
                )
                val zdt = ZonedDateTime.now(zone)
                val fmt = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
                ToolResult(
                    family, ToolStatus.SUCCESS,
                    "${zdt.format(fmt)} $zoneName",
                    "TIME_RESULT\nstatus=SUCCESS\nzone=$zoneName\ntime=${zdt.format(fmt)}"
                )
            }
            "diff" -> {
                if (cmd.args.size < 2) return error("diff needs two times")
                val t1 = parseTime(cmd.args[0]) ?: return error("bad time: ${cmd.args[0]}")
                val t2 = parseTime(cmd.args[1]) ?: return error("bad time: ${cmd.args[1]}")
                val minutes = Duration.between(t1, t2).toMinutes()
                val h = minutes / 60
                val m = minutes % 60
                ToolResult(
                    family, ToolStatus.SUCCESS,
                    "${h}h ${m}m",
                    "TIME_RESULT\nstatus=SUCCESS\ndiff=${h}h ${m}m"
                )
            }
            "convert" -> {
                if (cmd.args.size < 2) return error("convert needs time and zone")
                val fromZone = resolveZone(cmd.args[0]) ?: return error("bad zone: ${cmd.args[0]}")
                val toZone = resolveZone(cmd.args[1]) ?: return error("bad zone: ${cmd.args[1]}")
                val now = ZonedDateTime.now(fromZone)
                val converted = now.withZoneSameInstant(toZone)
                val fmt = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
                ToolResult(
                    family, ToolStatus.SUCCESS,
                    "${converted.format(fmt)} ${cmd.args[1]}",
                    "TIME_RESULT\nstatus=SUCCESS\nfrom=${now.format(fmt)} ${cmd.args[0]}\nto=${converted.format(fmt)} ${cmd.args[1]}"
                )
            }
            else -> error("unknown operation: ${cmd.operation}")
        }
    }

    private fun resolveZone(name: String): ZoneId? = try {
        ZoneId.of(name)
    } catch (e: Exception) {
        null
    }

    private fun parseTime(s: String): LocalTime? = try {
        LocalTime.parse(s)
    } catch (e: Exception) {
        null
    }

    private fun error(msg: String): ToolResult =
        ToolResult(family, ToolStatus.ERROR, msg, "TIME_RESULT\nstatus=ERROR\nreason=$msg")
}
