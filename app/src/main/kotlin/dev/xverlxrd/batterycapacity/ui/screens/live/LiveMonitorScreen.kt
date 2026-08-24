package dev.xverlxrd.batterycapacity.ui.screens.live

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.xverlxrd.batterycapacity.domain.model.ChargeStatus
import dev.xverlxrd.batterycapacity.domain.model.PowerSource
import dev.xverlxrd.batterycapacity.domain.model.BatterySnapshot
import dev.xverlxrd.batterycapacity.ui.components.ChartSeries
import dev.xverlxrd.batterycapacity.ui.components.InsetCard
import dev.xverlxrd.batterycapacity.ui.components.ListRow
import dev.xverlxrd.batterycapacity.ui.components.MultiLineChart
import kotlin.math.roundToInt

@Composable
fun LiveMonitorScreen(viewModel: LiveMonitorViewModel = hiltViewModel()) {
    val live by viewModel.live.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val info = live

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Сейчас", style = MaterialTheme.typography.headlineLarge)

        if (info == null) {
            Text(
                "Ожидание данных датчика…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        val s = info.snapshot

        // Герой: SOC крупно, как на циферблате.
        Column(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                s.socPercent?.roundToInt()?.toString()?.plus("%") ?: "—",
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = 64.sp),
                color = if (s.isCharging) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                statusText(s.status) + " · " + sourceText(s.plugged),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
        }

        InsetCard {
            ListRow(
                title = "Ток",
                value = s.currentMa?.let { "%+.0f мА".format(it) } ?: "—",
                valueColor = if (s.isCharging) MaterialTheme.colorScheme.tertiary else null,
            )
            ListRow(
                title = "Напряжение",
                value = s.voltageMv?.let { "%.3f В".format(it / 1000.0) } ?: "—",
            )
            ListRow(
                title = "Температура",
                value = s.temperatureC?.let { "%.1f °C".format(it) } ?: "—",
                showDivider = false,
            )
        }

        InsetCard {
            ListRow(
                title = "Скорость разряда",
                value = info.dischargeRatePerHour?.let { "%.1f %/ч".format(it) } ?: "—",
            )
            ListRow(
                title = "До отключения",
                value = info.etaMinutesToEmpty?.let { "~$it мин" } ?: "—",
                showDivider = false,
            )
        }

        if (info.plateauDetected) {
            Text(
                "SOC не меняется дольше 20 минут при текущем токе — вероятно, калибровочный дрейф газгейджа. Рекомендуется полный цикл разряд→заряд без отключения.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        InsetCard {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text(
                    "Напряжение и ток",
                    style = MaterialTheme.typography.titleMedium,
                )
                val voltagePoints = ArrayList<Pair<Float, Float>>(history.size)
                val currentPoints = ArrayList<Pair<Float, Float>>(history.size)
                history.forEachIndexed { index: Int, snap: BatterySnapshot ->
                    snap.voltageMv?.let { mv -> voltagePoints.add(index.toFloat() to mv / 1000f) }
                    snap.currentMa?.let { ma -> currentPoints.add(index.toFloat() to ma) }
                }
                Spacer(Modifier.height(12.dp))
                MultiLineChart(
                    series = listOf(
                        ChartSeries(voltagePoints, MaterialTheme.colorScheme.primary, "В"),
                        ChartSeries(currentPoints, MaterialTheme.colorScheme.tertiary, "мА"),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

private fun statusText(status: ChargeStatus): String = when (status) {
    ChargeStatus.CHARGING -> "Заряжается"
    ChargeStatus.DISCHARGING -> "Разряжается"
    ChargeStatus.FULL -> "Полный"
    ChargeStatus.NOT_CHARGING -> "Не заряжается"
    ChargeStatus.UNKNOWN -> "Неизвестно"
}

private fun sourceText(source: PowerSource): String = when (source) {
    PowerSource.AC -> "Сеть"
    PowerSource.USB -> "USB"
    PowerSource.WIRELESS -> "Беспроводная"
    PowerSource.DOCK -> "Док-станция"
    PowerSource.NONE -> "Аккумулятор"
}
