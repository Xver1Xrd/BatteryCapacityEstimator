package dev.xverlxrd.batterycapacity.data.datasource.sysfs

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Полный сбор телеметрии из /sys/class/power_supply: обходит все узлы
 * (battery, usb, dc, main, maxfg…), читает каждый доступный файл.
 * Это «сырые данные» для экрана Устройство и статистики — на Samsung
 * здесь живут fuel-gauge значения вроде fg_fullcapnom и battery_cycle,
 * недоступные через публичные API.
 */
@Singleton
class SysfsHarvester @Inject constructor(
    private val reader: SysfsReader,
) {

    data class Node(val key: String, val value: String)

    fun harvest(maxNodes: Int = 80): List<Node> {
        val dirs = reader.listDirs(POWER_SUPPLY_ROOT).sorted()
        val result = mutableListOf<Node>()
        for (dir in dirs) {
            val shortName = dir.substringAfterLast('/')
            val type = reader.readText("$dir/type") ?: "?"
            // Человекочитаемый заголовок-узел: тип питания из ядра.
            result += Node("$shortName/type", type)

            val files = reader.listFiles(dir)
                .map { it.substringAfterLast('/') }
                .filter { it != "uevent" && !it.startsWith(".") }
                .sorted()
            for (file in files) {
                if (result.size >= maxNodes) return result
                val value = reader.readText("$dir/$file") ?: continue
                if (value.isEmpty() || value.length > 120) continue
                result += Node("$shortName/$file", value)
            }
        }
        return result
    }

    companion object {
        private const val POWER_SUPPLY_ROOT = "/sys/class/power_supply"
    }
}
