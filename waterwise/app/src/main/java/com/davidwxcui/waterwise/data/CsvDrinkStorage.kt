package com.davidwxcui.waterwise.data

import android.content.Context
import java.io.File

/**
 * 负责把 DrinkLog 列表 存到
 *   /data/data/com.davidwxcui.waterwise/files/drinkLog.csv
 * 并从里面读出来。
 */
class CsvDrinkStorage(context: Context) {

    private val file: File = File(context.filesDir, "drinkLog.csv")
    private val header = "timestamp,type,volume_ml,effective_ml,note"

    /** 读取所有记录（如果没有文件就返回空列表） */
    fun loadAll(): List<DrinkLog> {
        if (!file.exists()) return emptyList()

        val lines = file.readLines()
            .filter { it.isNotBlank() }

        if (lines.isEmpty()) return emptyList()

        // 如果第一行是 header，就跳过
        val body = if (lines.first().startsWith("timestamp")) {
            lines.drop(1)
        } else {
            lines
        }

        return body.mapNotNull { parseLine(it) }
    }

    /** 覆盖写入所有记录（总是写一行 header + 多行明细） */
    fun saveAll(list: List<DrinkLog>) {
        file.printWriter().use { out ->
            out.println(header)
            list.forEach { log ->
                val noteEscaped = escape(log.note)
                out.println(
                    "${log.timeMillis}," +
                            "${log.type.name}," +
                            "${log.volumeMl}," +
                            "${log.effectiveMl}," +
                            noteEscaped
                )
            }
        }
    }

    private fun parseLine(line: String): DrinkLog? {
        // note 可能有逗号，所以限制拆成 5 段，最后一段全部作为 note
        val parts = line.split(",", limit = 5)
        if (parts.size < 4) return null

        val ts = parts[0].toLongOrNull() ?: return null
        val type = try {
            DrinkType.valueOf(parts[1])
        } catch (_: IllegalArgumentException) {
            return null
        }
        val vol = parts[2].toIntOrNull() ?: return null
        val eff = parts[3].toIntOrNull() ?: vol
        val note = if (parts.size >= 5) unescape(parts[4]) else null

        // 这里先给 id = 0，真正的 id 在 ViewModel 里按列表重新编号
        return DrinkLog(
            id = 0,
            type = type,
            volumeMl = vol,
            effectiveMl = eff,
            note = note,
            timeMillis = ts
        )
    }

    private fun escape(note: String?): String {
        if (note.isNullOrEmpty()) return ""
        val escaped = note.replace("\"", "\"\"")
        return "\"$escaped\""
    }

    private fun unescape(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null

        return if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length >= 2) {
            trimmed.substring(1, trimmed.length - 1)
                .replace("\"\"", "\"")
        } else {
            trimmed
        }
    }
}
