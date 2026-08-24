package dev.xverlxrd.batterycapacity.data.repository

import dev.xverlxrd.batterycapacity.data.local.dao.SampleDao
import dev.xverlxrd.batterycapacity.data.local.dao.SessionDao
import dev.xverlxrd.batterycapacity.data.local.entity.MeasurementSessionEntity
import dev.xverlxrd.batterycapacity.data.mapper.EntityMappers.toDomain
import dev.xverlxrd.batterycapacity.data.mapper.EntityMappers.toEntity
import dev.xverlxrd.batterycapacity.di.IoDispatcher
import dev.xverlxrd.batterycapacity.domain.model.BatterySnapshot
import dev.xverlxrd.batterycapacity.domain.model.CalibrationSession
import dev.xverlxrd.batterycapacity.domain.model.SessionState
import dev.xverlxrd.batterycapacity.domain.repository.MeasurementSessionRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MeasurementSessionRepositoryImpl @Inject constructor(
    private val sessionDao: SessionDao,
    private val sampleDao: SampleDao,
    @IoDispatcher private val io: CoroutineDispatcher,
) : MeasurementSessionRepository {

    override fun observeActiveSession(): Flow<CalibrationSession?> = sessionDao.observeActive()
        .map { entity ->
            entity?.let {
                val count = sampleDao.countBySession(it.id)
                it.toDomain(count)
            }
        }
        .flowOn(io)

    override fun observeSamples(sessionId: Long): Flow<List<BatterySnapshot>> =
        sampleDao.observeBySession(sessionId).map { list -> list.map { it.toDomain() } }.flowOn(io)

    override suspend fun start(socNow: Float?, nowMs: Long): Long = withContext(io) {
        sessionDao.insert(
            MeasurementSessionEntity(
                startedAtMs = nowMs,
                updatedAtMs = nowMs,
                state = SessionState.WAITING_DISCHARGE.name,
                socAtStart = socNow,
                lowestSoc = socNow,
                lastSoc = socNow,
                collectedDeltaUah = 0,
                sampleCount = 0,
                pauseReason = null,
            ),
        )
    }

    override suspend fun recordSample(sessionId: Long, snapshot: BatterySnapshot) = withContext(io) {
        val soc = snapshot.socPercent ?: return@withContext
        sampleDao.insert(snapshot.toEntity(sessionId))
        val first = sampleDao.loadBySession(sessionId).firstOrNull()
        val deltaUah = if (first != null && snapshot.chargeCounterUah != null && first.chargeCounterUah != null) {
            kotlin.math.abs(snapshot.chargeCounterUah - first.chargeCounterUah)
        } else {
            0L
        }
        sessionDao.touchProgress(
            id = sessionId,
            soc = soc,
            deltaUah = deltaUah,
            sampleCount = sampleDao.countBySession(sessionId),
            nowMs = System.currentTimeMillis(),
        )
    }

    override suspend fun loadSamples(sessionId: Long): List<BatterySnapshot> = withContext(io) {
        sampleDao.loadBySession(sessionId).map { it.toDomain() }
    }

    override suspend fun updateState(sessionId: Long, state: SessionState, pauseReason: String?) =
        withContext(io) { sessionDao.updateState(sessionId, state.name, pauseReason, System.currentTimeMillis()) }

    override suspend fun touch(sessionId: Long, socNow: Float?) = withContext(io) {
        val entity = sessionDao.byId(sessionId) ?: return@withContext
        sessionDao.touchProgress(
            id = sessionId,
            soc = socNow ?: entity.lastSoc,
            deltaUah = entity.collectedDeltaUah,
            sampleCount = entity.sampleCount,
            nowMs = System.currentTimeMillis(),
        )
    }

    override suspend fun pauseAllActive(reason: String) {
        val active = sessionDao.findActive() ?: return
        sessionDao.updateState(active.id, SessionState.PAUSED.name, reason, System.currentTimeMillis())
    }

    override suspend fun cancelActive() = withContext(io) {
        val active = sessionDao.findActive() ?: return@withContext
        sessionDao.updateState(active.id, SessionState.CANCELLED.name, null, System.currentTimeMillis())
        sampleDao.deleteBySession(active.id)
        sessionDao.delete(active.id)
    }

    override suspend fun findResumable(): CalibrationSession? = withContext(io) {
        val entity = sessionDao.findActive() ?: return@withContext null
        entity.toDomain(sampleDao.countBySession(entity.id))
    }

    override suspend fun delete(sessionId: Long) = withContext(io) {
        sampleDao.deleteBySession(sessionId)
        sessionDao.delete(sessionId)
    }
}
