package dev.xverlxrd.batterycapacity.data.repository

import dev.xverlxrd.batterycapacity.data.local.dao.CompletedMeasurementDao
import dev.xverlxrd.batterycapacity.data.mapper.EntityMappers.toDomain
import dev.xverlxrd.batterycapacity.data.mapper.EntityMappers.toEntity
import dev.xverlxrd.batterycapacity.di.IoDispatcher
import dev.xverlxrd.batterycapacity.domain.model.CapacityEstimate
import dev.xverlxrd.batterycapacity.domain.repository.CompletedMeasurementsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CompletedMeasurementsRepositoryImpl @Inject constructor(
    private val dao: CompletedMeasurementDao,
    @IoDispatcher private val io: CoroutineDispatcher,
) : CompletedMeasurementsRepository {

    override fun observeHistory(): Flow<List<CapacityEstimate>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }.flowOn(io)

    override suspend fun save(estimate: CapacityEstimate) {
        withContext(io) { dao.insert(estimate.toEntity()) }
    }

    override suspend fun delete(estimate: CapacityEstimate) = withContext(io) {
        dao.deleteByMeasuredAt(estimate.measuredAtMs)
    }
}
