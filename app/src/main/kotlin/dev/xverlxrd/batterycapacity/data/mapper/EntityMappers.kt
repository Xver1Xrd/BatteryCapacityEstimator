package dev.xverlxrd.batterycapacity.data.mapper

import dev.xverlxrd.batterycapacity.data.local.entity.BatterySampleEntity
import dev.xverlxrd.batterycapacity.data.local.entity.CompletedMeasurementEntity
import dev.xverlxrd.batterycapacity.data.local.entity.MeasurementSessionEntity
import dev.xverlxrd.batterycapacity.domain.model.BatterySnapshot
import dev.xverlxrd.batterycapacity.domain.model.CalibrationSession
import dev.xverlxrd.batterycapacity.domain.model.CapacityEstimate
import dev.xverlxrd.batterycapacity.domain.model.MeasurementMethod
import dev.xverlxrd.batterycapacity.domain.model.SessionState

/** Мапперы Room-сущностей ↔ доменных моделей. */
object EntityMappers {

    fun MeasurementSessionEntity.toDomain(samples: Int = sampleCount): CalibrationSession =
        CalibrationSession(
            id = id,
            startedAtMs = startedAtMs,
            updatedAtMs = updatedAtMs,
            state = SessionState.valueOf(state),
            socAtStart = socAtStart,
            lowestSoc = lowestSoc,
            lastSoc = lastSoc,
            collectedDeltaUah = collectedDeltaUah,
            sampleCount = samples,
            pauseReason = pauseReason,
        )

    fun BatterySampleEntity.toDomain(): BatterySnapshot = BatterySnapshot(
        timestampMs = timestampMs,
        socPercent = socPercent,
        voltageMv = voltageMv,
        currentMa = currentMa,
        chargeCounterUah = chargeCounterUah,
        temperatureC = tempTenthsC?.div(10.0),
    )

    fun BatterySnapshot.toEntity(sessionId: Long): BatterySampleEntity {
        val soc = requireNotNull(socPercent) { "SOC обязателен для записи сэмпла" }
        return BatterySampleEntity(
            sessionId = sessionId,
            timestampMs = timestampMs,
            socPercent = soc,
            voltageMv = voltageMv,
            currentMa = currentMa,
            chargeCounterUah = chargeCounterUah,
            tempTenthsC = temperatureC?.let { (it * 10).toInt() },
        )
    }

    fun CompletedMeasurementEntity.toDomain(): CapacityEstimate = CapacityEstimate(
        actualMah = actualMah,
        designMah = designMah,
        method = MeasurementMethod.valueOf(method),
        confidenceMah = confidenceMah,
        socSpanPct = socSpanPct,
        sampleCount = sampleCount,
        measuredAtMs = measuredAtMs,
        warnings = warnings?.split("\n")?.filter { it.isNotBlank() } ?: emptyList(),
    )

    fun CapacityEstimate.toEntity(): CompletedMeasurementEntity = CompletedMeasurementEntity(
        measuredAtMs = measuredAtMs,
        actualMah = actualMah,
        designMah = designMah,
        method = method.name,
        confidenceMah = confidenceMah,
        socSpanPct = socSpanPct,
        sampleCount = sampleCount,
        warnings = warnings.joinToString("\n").takeIf { it.isNotBlank() },
    )
}
