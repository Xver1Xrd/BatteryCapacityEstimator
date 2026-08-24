package dev.xverlxrd.batterycapacity

import android.content.Intent
import android.os.BatteryManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import dev.xverlxrd.batterycapacity.data.datasource.system.BatteryStatusIntentMapper
import dev.xverlxrd.batterycapacity.domain.model.ChargeStatus
import dev.xverlxrd.batterycapacity.domain.model.PowerSource
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner

/**
 * Robolectric: ACTION_BATTERY_CHANGED extras → доменный снимок.
 * Проверяем маппинг констант BatteryManager на реальных значениях intent'а.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BatteryStatusIntentMapperTest {

    private fun batteryIntent(
        level: Int = 73,
        scale: Int = 100,
        voltage: Int = 4021,
        tempTenths: Int = 287,
        status: Int = BatteryManager.BATTERY_STATUS_DISCHARGING,
        plugged: Int = 0,
        health: Int = BatteryManager.BATTERY_HEALTH_GOOD,
    ): Intent = Intent(Intent.ACTION_BATTERY_CHANGED).apply {
        putExtra(BatteryManager.EXTRA_LEVEL, level)
        putExtra(BatteryManager.EXTRA_SCALE, scale)
        putExtra(BatteryManager.EXTRA_VOLTAGE, voltage)
        putExtra(BatteryManager.EXTRA_TEMPERATURE, tempTenths)
        putExtra(BatteryManager.EXTRA_STATUS, status)
        putExtra(BatteryManager.EXTRA_PLUGGED, plugged)
        putExtra(BatteryManager.EXTRA_HEALTH, health)
    }

    @Test
    fun dischargingIntent_mapsCorrectly() {
        val intent = batteryIntent()
        val snapshot = BatteryStatusIntentMapper.map(
            level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1),
            scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1),
            voltageMv = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1),
            tempTenthsC = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1),
            statusCode = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1),
            pluggedCode = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0),
            healthCode = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1),
            nowMs = 123L,
        )

        assertEquals(73f, snapshot.socPercent!!)
        assertEquals(4021, snapshot.voltageMv)
        assertEquals(28.7, snapshot.temperatureC!!, 1e-9)
        assertEquals(ChargeStatus.DISCHARGING, snapshot.status)
        assertEquals(PowerSource.NONE, snapshot.plugged)
    }

    @Test
    fun wirelessChargingIntent_mapsPluggedAndFull() {
        val snapshot = BatteryStatusIntentMapper.map(
            level = 100,
            scale = 100,
            voltageMv = 4300,
            tempTenthsC = 310,
            statusCode = BatteryManager.BATTERY_STATUS_FULL,
            pluggedCode = BatteryManager.BATTERY_PLUGGED_WIRELESS,
            healthCode = BatteryManager.BATTERY_HEALTH_COLD,
            nowMs = 0L,
        )

        assertEquals(ChargeStatus.FULL, snapshot.status)
        assertEquals(PowerSource.WIRELESS, snapshot.plugged)
    }
}
