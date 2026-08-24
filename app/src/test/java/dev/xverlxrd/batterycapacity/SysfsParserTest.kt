package dev.xverlxrd.batterycapacity

import dev.xverlxrd.batterycapacity.data.datasource.sysfs.SysfsBatterySource
import dev.xverlxrd.batterycapacity.data.datasource.sysfs.SysfsReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Чтение sysfs на фейковых деревьях файлов: Pixel (maxfg), складной с двумя банками. */
class SysfsParserTest {

    /** Фейковый ридер: карта «путь → содержимое». */
    private class FakeReader(private val files: Map<String, String>) : SysfsReader {
        override fun listDirs(root: String): List<String> =
            files.keys.filter { it.startsWith("$root/") }
                .map { it.removePrefix("$root/").substringBefore('/') }
                .distinct()
                .map { "$root/$it" }

        override fun exists(path: String): Boolean = files.containsKey(path)

        override fun readText(path: String): String? = files[path]?.trim()
    }

    @Test
    fun pixel7Pro_maxfgNode_readsCapacityPair() {
        // У Pixel главный fuel gauge — узел maxfg рядом с battery.
        val reader = FakeReader(
            mapOf(
                "/sys/class/power_supply/battery/type" to "Battery",
                "/sys/class/power_supply/battery/capacity" to "64",
                "/sys/class/power_supply/battery/status" to "Discharging",
                "/sys/class/power_supply/battery/temp" to "263",
                "/sys/class/power_supply/battery/voltage_now" to "3812000",
                "/sys/class/power_supply/battery/current_now" to "-412000",
                "/sys/class/power_supply/maxfg/type" to "Battery",
                "/sys/class/power_supply/maxfg/charge_full" to "4211000",
                "/sys/class/power_supply/maxfg/charge_full_design" to "5003000",
            ),
        )
        val source = SysfsBatterySource(reader)
        val snapshot = source.readAll(nowMs = 0)!!

        assertNotNull(snapshot)
        // charge_full в мкА·ч → 4211 мА·ч; design → 5003 мА·ч.
        assertEquals(4211.0, snapshot.chargeFullMah!!, 0.5)
        assertEquals(5003.0, snapshot.chargeFullDesignMah!!, 0.5)
        assertEquals(26.3, snapshot.temperatureC!!, 1e-9)
        // Ток -412 мкА → -412 мА? Нет: -412000 мкА = -412 мА.
        assertEquals(-412.0, snapshot.currentMa!!.toDouble(), 0.5)
        assertEquals(3812, snapshot.voltageMv)
    }

    @Test
    fun foldable_twoBatteries_aggregated() {
        val reader = FakeReader(
            mapOf(
                // Складной: две банки по ~2200 мА·ч.
                "/sys/class/power_supply/battery/type" to "Battery",
                "/sys/class/power_supply/battery/capacity" to "50",
                "/sys/class/power_supply/battery/charge_full" to "2000000",
                "/sys/class/power_supply/battery/status" to "Charging",
                "/sys/class/power_supply/main_battery/type" to "Battery",
                "/sys/class/power_supply/main_battery/capacity" to "70",
                "/sys/class/power_supply/main_battery/charge_full" to "2400000",
                "/sys/class/power_supply/main_battery/status" to "Charging",
            ),
        )
        val source = SysfsBatterySource(reader)
        val snapshot = source.readAll(nowMs = 0)!!

        // Сумма фактических ёмкостей: 2000 + 2400 = 4400 мА·ч.
        assertEquals(4400.0, snapshot.chargeFullMah!!, 1.0)
        // Взвешенный SOC: (2000*50 + 2400*70)/4400 ≈ 60.9%.
        assertEquals(60.9f, snapshot.socPercent!!, 0.2f)
    }

    @Test
    fun energyBasedDevice_convertsUwhToMah() {
        val reader = FakeReader(
            mapOf(
                "/sys/class/power_supply/bms/type" to "Battery",
                "/sys/class/power_supply/bms/energy_full" to "17000000",   // 17 Вт·ч
                "/sys/class/power_supply/bms/energy_full_design" to "19000000", // 19 Вт·ч
                "/sys/class/power_supply/bms/voltage_max_design" to "3850000",
            ),
        )
        val source = SysfsBatterySource(reader)
        val snapshot = source.readAll(nowMs = 0)!!

        // µWh/1000/В = мА·ч: 17 000 000/1000/3.85 ≈ 4416 мА·ч.
        assertEquals(4415.6, snapshot.energyFullMah!!, 5.0)
        assertEquals(4935.1, snapshot.energyFullDesignMah!!, 5.0)
    }

    @Test
    fun noBatteryNodes_returnsNull() {
        val reader = FakeReader(
            mapOf(
                "/sys/class/power_supply/usb/type" to "USB",
            ),
        )
        assertNull(SysfsBatterySource(reader).readAll(nowMs = 0))
    }
}
