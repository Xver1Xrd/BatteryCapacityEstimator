package dev.xverlxrd.batterycapacity.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Единая spacing-система. Все отступы в приложении берутся отсюда;
 * произвольные dp-значения допускаются только при веской причине.
 */
object Spacing {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp
    val huge = 40.dp

    /** Горизонтальный отступ контента экрана от краёв. */
    val screenH = xl
}

/** Единая система скруглений: мелкие элементы → карточки → листы → пилюли. */
object Radius {
    val s = 10.dp   // бейджи, чипы, миниатюрные поверхности
    val m = 16.dp   // карточки, кнопки, поля ввода
    val l = 24.dp   // крупные hero-панели, диалоги
}
