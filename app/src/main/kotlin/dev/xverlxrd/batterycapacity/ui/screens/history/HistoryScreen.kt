package dev.xverlxrd.batterycapacity.ui.screens.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.xverlxrd.batterycapacity.domain.model.CapacityEstimate
import dev.xverlxrd.batterycapacity.ui.components.EmptyState
import dev.xverlxrd.batterycapacity.ui.components.GroupSurface
import dev.xverlxrd.batterycapacity.ui.components.HealthPoint
import dev.xverlxrd.batterycapacity.ui.components.HealthTrendChart
import dev.xverlxrd.batterycapacity.ui.components.MetricRow
import dev.xverlxrd.batterycapacity.ui.components.SectionHeader
import dev.xverlxrd.batterycapacity.ui.theme.LocalStatusColors
import dev.xverlxrd.batterycapacity.ui.theme.healthColor
import dev.xverlxrd.batterycapacity.ui.theme.Spacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * История: график «здоровье со временем» с выбором точки + список измерений.
 * Выбор синхронизирован между графиком и списком.
 */
@Composable
fun HistoryScreen(
    onStartMeasurement: () -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val history by viewModel.history.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<Int?>(null) }
    val statusColors = LocalStatusColors.current

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screenH, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        Text("История", style = MaterialTheme.typography.headlineLarge)

        if (history.isEmpty()) {
            EmptyState(
                title = "Пока нет измерений",
                body = "Здесь появится график деградации батареи после первого завершённого теста.",
                ctaText = "Измерить ёмкость",
                onCta = onStartMeasurement,
            )
            return@Column
        }

        // График строится в хронологическом порядке (старые слева).
        val chronological = history.asReversed()
        val points = chronological.mapNotNull { m ->
            m.healthPercent?.let {
                HealthPoint(
                    timeMs = m.measuredAtMs,
                    label = shortDate(m.measuredAtMs),
                    healthPercent = it,
                    capacityMah = m.actualMah.roundToInt(),
                )
            }
        }
        val selectedMeasurement = selected?.let { chronological.getOrNull(it) }

        if (points.size >= 2) {
            SectionHeader("Здоровье со временем")
            Spacer(Modifier.padding(top = 4.dp))
            GroupSurface {
                HealthTrendChart(
                    points = points,
                    selectedIndex = selected,
                    onSelect = { selected = it },
                    lineColor = statusColors.success,
                    modifier = Modifier.padding(top = 16.dp, start = 16.dp, end = 16.dp),
                )
                MetricRow(
                    label = selectedMeasurement?.let { shortDate(it.measuredAtMs) } ?: "Коснитесь точки на графике",
                    value = selectedMeasurement?.let { m ->
                        "${m.healthPercent?.roundToInt()} % · ${m.actualMah.roundToInt()} мА·ч"
                    } ?: "",
                    showDivider = false,
                )
            }
        }

        SectionHeader("Измерения")
        history.forEachIndexed { index, measurement ->
            GroupSurface(
                onClick = {
                    val chronoIndex = history.size - 1 - index
                    selected = if (selected == chronoIndex) null else chronoIndex
                },
            ) {
                HistoryEntryRow(measurement, onDelete = { viewModel.delete(measurement) })
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun HistoryEntryRow(measurement: CapacityEstimate, onDelete: () -> Unit) {
    val statusColors = LocalStatusColors.current
    val health = measurement.healthPercent
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable { }
            .padding(start = Spacing.l, end = Spacing.s, top = Spacing.m, bottom = Spacing.m),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                SimpleDateFormat("d MMM yyyy", Locale("ru")).format(Date(measurement.measuredAtMs)),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "${measurement.method.displayName} · диапазон ${measurement.socSpanPct.roundToInt()} %",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                health?.let { "${it.roundToInt()} %" } ?: "—",
                style = MaterialTheme.typography.headlineSmall,
                color = health?.let(statusColors::healthColor) ?: MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "${measurement.actualMah.roundToInt()} мА·ч",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete, modifier = Modifier.heightIn(min = 44.dp)) {
            Icon(
                Icons.Outlined.DeleteOutline,
                contentDescription = "Удалить измерение",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun shortDate(ms: Long): String =
    SimpleDateFormat("d MMM", Locale("ru")).format(Date(ms))
