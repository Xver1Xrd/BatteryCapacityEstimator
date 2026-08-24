package dev.xverlxrd.batterycapacity.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.xverlxrd.batterycapacity.ui.theme.Radius
import dev.xverlxrd.batterycapacity.ui.theme.Spacing

/**
 * Базовая группирующая поверхность: плоская карточка без теней,
 * скругление Radius.m. Фундамент всех секций приложения.
 */
@Composable
fun GroupSurface(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (onClick != null) {
        val interaction = remember { MutableInteractionSource() }
        Card(
            modifier = modifier.fillMaxWidth().pressScale(interaction),
            shape = MaterialTheme.shapes.medium,
            interactionSource = interaction,
            onClick = onClick,
            colors = CardDefaults.cardColors(containerColor = containerColor),
        ) { Column(content = content) }
    } else {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = containerColor,
        ) { Column(content = content) }
    }
}

/** Заголовок секции: капс-надпись tertiary + необязательное действие справа. */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, trailing: (@Composable RowScope.() -> Unit)? = null) {
    Row(
        modifier = modifier.fillMaxWidth().padding(start = Spacing.s, end = Spacing.s, top = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (trailing != null) {
            Row(Modifier.weight(1f), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End) { trailing() }
        }
    }
}
