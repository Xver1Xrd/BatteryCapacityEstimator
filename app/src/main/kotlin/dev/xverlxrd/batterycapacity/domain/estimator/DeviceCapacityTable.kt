package dev.xverlxrd.batterycapacity.domain.estimator

/**
 * Таблица паспортных ёмкостей известных моделей (мА·ч, номинал).
 * Используется, если sysfs не отдаёт charge_full_design и пользователь
 * не ввёл ёмкость вручную. Ключ — подстрока manufacturer+model в нижнем регистре.
 *
 * Портируемость: чистый Kotlin без Android-зависимостей — переезжает на iOS как есть.
 */
object DeviceCapacityTable {

    private val table: List<Pair<String, Int>> = listOf(
        // Google Pixel
        "google pixel 4a" to 3140,
        "google pixel 5" to 4080,
        "google pixel 6" to 4614,
        "google pixel 6a" to 4410,
        "google pixel 6 pro" to 5003,
        "google pixel 7" to 4355,
        "google pixel 7a" to 4385,
        "google pixel 7 pro" to 5000,
        "google pixel 8" to 4575,
        "google pixel 8a" to 4492,
        "google pixel 8 pro" to 5050,
        "google pixel 9" to 4700,
        "google pixel 9a" to 5100,
        "google pixel 9 pro" to 5060,
        // Samsung Galaxy (типичные номиналы EU-версий)
        "sm-g991" to 4000,   // S21
        "sm-g998" to 5000,   // S21 Ultra
        "sm-s901" to 3700,   // S22
        "sm-s908" to 5000,   // S22 Ultra
        "sm-s911" to 3900,   // S23
        "sm-s918" to 5000,   // S23 Ultra
        "sm-s921" to 4000,   // S24
        "sm-s926" to 4900,   // S24+
        "sm-s928" to 5000,   // S24 Ultra
        // Xiaomi / Redmi / POCO
        "22011" to 4500,     // Xiaomi 12
        "22111" to 4500,     // Xiaomi 13
        "23127" to 5000,     // Xiaomi 14
        "poco f5" to 5000,
        "poco x6" to 5100,
    )

    /** @return паспортная ёмкость или null для неизвестной модели. */
    fun designCapacityMah(manufacturer: String?, model: String?): Double? {
        val key = "${manufacturer.orEmpty()} ${model.orEmpty()}".lowercase().trim()
        if (key.isBlank()) return null
        return table.firstOrNull { (prefix, _) -> key.contains(prefix) }?.second?.toDouble()
    }
}
