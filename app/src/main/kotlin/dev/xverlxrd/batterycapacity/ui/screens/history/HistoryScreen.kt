package dev.xverlxrd.batterycapacity.ui.screens.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.xverlxrd.batterycapacity.domain.model.CapacityEstimate
import dev.xverlxrd.batterycapacity.ui.components.InsetCard
import dev.xverlxrd.batterycapacity.ui.components.ListRow
import dev.xverlxrd.batterycapacity.ui.components.VerdictBadge
import dev.xverlxrd.batterycapacity.ui.theme.healthColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun HistoryScreen(viewModel: HistoryViewModel = hiltViewModel()) {
    val history by viewModel.history.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text("История", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(14.dp))

        if (history.isEmpty()) {
            Text(
                "Здесь появятся результаты измерений — по ним видно, как деградирует батарея со временем.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        // Тренд деградации: изменение здоровья между последними измерениями.
        val newest = history.first()
        val oldest = history.last()
        val drift = newest.healthPercent?.minus(oldest.healthPercent ?: 0.0)

        if (drift != null && history.size >= 2) {
            InsetCard {
                ListRow(
                    title = "Изменение с первой записи",
                    value = "%+.1f п.п.".format(drift),
                    valueColor = if (drift < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                    showDivider = false,
                )
            }
            Spacer(Modifier.height(14.dp))
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsIndexed(history, key = { _, item -> item.measuredAtMs }) { _, measurement ->
                HistoryRow(measurement, onDelete = { viewModel.delete(measurement) })
            }
        }
    }
}

private val dateFormat = SimpleDateFormat("d MMM yyyy, HH:mm", Locale("ru"))

@Composable
private fun HistoryRow(measurement: CapacityEstimate, onDelete: () -> Unit) {
    InsetCard {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(dateFormat.format(Date(measurement.measuredAtMs)), style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${measurement.actualMah.roundToInt()} мА·ч ±${measurement.confidenceMah.roundToInt()} • ${measurement.method.displayName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    measurement.designMah?.let {
                        Text(
                            "Паспорт: ${it.roundToInt()} мА·ч",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                val health = measurement.healthPercent
                if (health != null) {
                    VerdictBadge("${health.roundToInt()}%", healthColor(health))
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        contentDescription = "Удалить измерение",
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
        }
    }
}
