package dev.xverlxrd.batterycapacity.ui.screens.device

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.xverlxrd.batterycapacity.domain.model.ChargeStatus
import dev.xverlxrd.batterycapacity.domain.model.LiveBatteryInfo
import dev.xverlxrd.batterycapacity.domain.model.PowerSource
import dev.xverlxrd.batterycapacity.ui.components.ErrorState
import dev.xverlxrd.batterycapacity.ui.components.GroupSurface
import dev.xverlxrd.batterycapacity.ui.components.MetricRow
import dev.xverlxrd.batterycapacity.ui.components.SectionHeader
import dev.xverlxrd.batterycapacity.ui.theme.LocalStatusColors
import dev.xverlxrd.batterycapacity.ui.theme.Spacing
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Технический экран устройства: состояние, электрика, системные данные.
 * Живые значения обновляются каждые ~2 с; инженерный шум остаётся здесь,
 * не на главном экране.
 */
@Composable
fun DeviceScreen(viewModel: DeviceViewModel = hiltViewModel()) {
    val live by viewModel.live.collectAsStateWithLifecycle()
    val statusColors = LocalStatusColors.current

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screenH, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        Text("Устройство", style = MaterialTheme.typography.headlineLarge)
        Text(
            Build.MODEL?.removePrefix("google ") ?: "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 0.dp),
        )

        val info = live
        if (info == null) {
            // Данные ещё не пришли — тихая заглушка вместо спиннера.
            GroupSurface {
                MetricRow("Заряд", "…")
                MetricRow("Напряжение", "…")
                MetricRow("Ток", "…", showDivider = false)
            }
        } else {
            StatusSection(info)
            ElectricalSection(info)
            SystemSection(info)

            if (info.plateauDetected) {
                ErrorState(
                    title = "Плато напряжения",
                    message = "Показания стабилизировались — оценка калибруется. Это нормальное поведение.",
                    tone = statusColors.info,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun StatusSection(info: LiveBatteryInfo) {
    val snap = info.snapshot
    SectionHeader("Состояние")
    Spacer(Modifier.height(4.dp))
    GroupSurface {
        MetricRow(
            label = "Уровень заряда",
            value = snap.socPercent?.let { "${it.roundToInt()} %" } ?: "—",
            valueColor = MaterialTheme.colorScheme.onSurface,
        )
        MetricRow(
            label = "Статус",
            value = when (snap.status) {
                ChargeStatus.CHARGING -> "Заряжается"
                ChargeStatus.DISCHARGING -> "Разряжается"
                ChargeStatus.FULL -> "Полный"
                ChargeStatus.NOT_CHARGING -> "Не заряжается"
                ChargeStatus.UNKNOWN -> "Неизвестно"
            },
            numericValue = false,
        )
        MetricRow(
            label = "Источник питания",
            value = when (snap.plugged) {
                PowerSource.AC -> "Сеть"
                PowerSource.USB -> "USB"
                PowerSource.WIRELESS -> "Беспроводная"
                PowerSource.DOCK -> "Док"
                PowerSource.NONE -> "—"
            },
            numericValue = false,
            showDivider = false,
        )
    }
}

@Composable
private fun ElectricalSection(info: LiveBatteryInfo) {
    val snap = info.snapshot
    val statusColors = LocalStatusColors.current
    SectionHeader("Электрика")
    Spacer(Modifier.height(4.dp))
    GroupSurface {
        MetricRow(
            label = "Напряжение",
            value = snap.voltageMv?.let { "%.2f В".format(it / 1000.0) } ?: "—",
        )
        MetricRow(
            label = "Ток",
            value = snap.currentMa?.let { "${if (it >= 0) "+" else ""}${it.roundToInt()} мА" } ?: "—",
            valueColor = snap.currentMa?.let { if (it > 0) statusColors.success else MaterialTheme.colorScheme.onSurfaceVariant },
        )
        MetricRow(
            label = "Мощность",
            value = powerText(snap.voltageMv, snap.currentMa),
        )
        MetricRow(
            label = "Температура",
            value = snap.temperatureC?.let { "%.1f °C".format(it) } ?: "—",
        )
        MetricRow(
            label = "Скорость разряда",
            value = info.dischargeRatePerHour?.let { "%.1f %%/ч".format(abs(it)) } ?: "—",
            showDivider = false,
        )
    }
}

@Composable
private fun SystemSection(info: LiveBatteryInfo) {
    val snap = info.snapshot
    SectionHeader("Системные данные")
    Spacer(Modifier.height(4.dp))
    GroupSurface {
        MetricRow("Циклов зарядки", snap.cycleCount?.toString() ?: "не сообщается")
        MetricRow("charge_full (sysfs)", snap.chargeFullMah?.let { "${it.roundToInt()} мА·ч" } ?: "—")
        MetricRow("charge_full_design (sysfs)", snap.chargeFullDesignMah?.let { "${it.roundToInt()} мА·ч" } ?: "—")
        MetricRow(
            "energy_full / design",
            listOfNotNull(
                snap.energyFullMah?.roundToInt()?.toString(),
                snap.energyFullDesignMah?.roundToInt()?.toString(),
            ).takeIf { it.size == 2 }?.joinToString(" / ") { "$it мА·ч" } ?: "—",
            showDivider = false,
        )
    }
}

private fun powerText(voltageMv: Int?, currentMa: Float?): String {
    if (voltageMv == null || currentMa == null) return "—"
    val watts = voltageMv / 1000.0 * abs(currentMa) / 1000.0
    return "%.2f Вт".format(watts)
}
