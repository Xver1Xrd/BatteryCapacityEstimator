package dev.xverlxrd.batterycapacity

import dev.xverlxrd.batterycapacity.data.datasource.dumpsys.DumpsysOutputParser
import dev.xverlxrd.batterycapacity.domain.model.ChargeStatus
import dev.xverlxrd.batterycapacity.domain.model.PowerSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Парсинг dumpsys battery на реальных фикстурах трёх OEM. */
class DumpsysOutputParserTest {

    @Test
    fun pixel7Pro_fixture_parses() {
        val output = """
            Current Battery Service state:
              AC powered: false
              USB powered: true
              Wireless powered: false
              Max charging current: 500000
              Max charging voltage: 9000000
              Charge counter: 4123456
              status: 2
              health: 2
              present: true
              level: 82
              scale: 100
              voltage: 4213
              temperature: 298
              technology: Li-poly
              cycle_count: 187
        """.trimIndent()

        val fields = DumpsysOutputParser.parse(output)
        assertNotNull(fields)
        assertEquals(82f, fields!!.levelPercent)
        assertEquals(4213, fields.voltageMv)
        assertEquals(4_123_456L, fields.chargeCounterUah)
        assertEquals(29.8, fields.tempTenthsC!! / 10.0, 1e-9)
        assertEquals(187, fields.cycleCount)

        val snapshot = DumpsysOutputParser.toSnapshot(fields, nowMs = 0)
        assertEquals(PowerSource.USB, snapshot.plugged)
        assertEquals(ChargeStatus.CHARGING, snapshot.status)
    }

    @Test
    fun samsungS23_fixture_parses() {
        val output = """
            Current Battery Service state:
              AC powered: false
              USB powered: false
              Wireless powered: true
              Max charging current: 0
              Charge counter: 2876543
              status: 5
              health: 2
              present: true
              level: 58
              scale: 100
              voltage: 4056
              temperature: 315
              technology: Li-ion
        """.trimIndent()

        val fields = DumpsysOutputParser.parse(output)!!
        val snapshot = DumpsysOutputParser.toSnapshot(fields, nowMs = 0)
        assertEquals(PowerSource.WIRELESS, snapshot.plugged)
        // status 5 → FULL
        assertEquals(ChargeStatus.FULL, snapshot.status)
    }

    @Test
    fun xiaomi13_fixture_parses_discharging() {
        val output = """
            Current Battery Service state:
              AC powered: false
              USB powered: false
              Wireless powered: false
              Max charging current: 0
              Charge counter: 0
              status: 3
              health: 2
              present: true
              level: 33
              scale: 100
              voltage: 3742
              temperature: 271
              technology: Li-poly
              cycle_count: 342
        """.trimIndent()

        val fields = DumpsysOutputParser.parse(output)!!
        val snapshot = DumpsysOutputParser.toSnapshot(fields, nowMs = 0)
        assertEquals(PowerSource.NONE, snapshot.plugged)
        assertEquals(ChargeStatus.DISCHARGING, snapshot.status)
        // Нулевой счётчик (OEM-заглушка) парсится как есть — решение о fallback принимает estimator.
        assertEquals(0L, fields.chargeCounterUah)
    }

    @Test
    fun garbageInput_returnsNull() {
        assertNull(DumpsysOutputParser.parse("Error: unknown service"))
    }
}
