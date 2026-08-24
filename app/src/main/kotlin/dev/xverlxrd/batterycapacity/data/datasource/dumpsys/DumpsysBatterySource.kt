package dev.xverlxrd.batterycapacity.data.datasource.dumpsys

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Запуск `dumpsys battery` и парсинг результата.
 * Требует permission DUMP, которого у обычных приложений нет —
 * потому все ошибки глотаются и возвращается null (graceful degradation).
 * Для отладки: `adb shell pm grant <pkg> android.permission.DUMP`.
 */
@Singleton
class DumpsysBatterySource @Inject constructor() {

    fun read(nowMs: Long): dev.xverlxrd.batterycapacity.domain.model.BatterySnapshot? = runCatching {
        val process = ProcessBuilder("dumpsys", "battery")
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor()
        DumpsysOutputParser.parse(output)?.let { DumpsysOutputParser.toSnapshot(it, nowMs) }
    }.getOrNull()
}
