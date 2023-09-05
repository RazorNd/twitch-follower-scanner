/*
 * Copyright 2023 Daniil <RazorNd> Razorenov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:Suppress("SpringDataRepositoryMethodReturnTypeInspection")

package ru.razornd.twitch.followers

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.data.domain.Pageable
import org.springframework.data.r2dbc.core.*
import org.springframework.data.relational.core.query.Criteria
import org.springframework.data.relational.core.query.Query
import org.springframework.data.relational.core.query.Update
import org.springframework.data.repository.Repository
import java.time.Instant
import org.springframework.data.r2dbc.repository.Query as RawQuery

interface ScanRepository : Repository<FollowerScan, Any> {
    fun findByStreamerIdOrderByScanNumberDesc(
        streamerId: String,
        pageable: Pageable = Pageable.unpaged()
    ): Flow<FollowerScan>

    suspend fun findTopByStreamerIdOrderByScanNumberDesc(streamerId: String): FollowerScan?

    suspend fun save(scan: FollowerScan): FollowerScan

}

interface FollowerRepository : Repository<Follower, Any>, FollowerUpsert {

    fun findByStreamerId(streamerId: String): Flow<Follower>

    fun findByStreamerIdAndScanNumber(streamerId: String, scanNumber: Int): Flow<Follower>

    fun findByStreamerIdAndScanNumberLessThan(streamerId: String, scanNumber: Int): Flow<Follower>

}

interface FollowerUpsert {

    suspend fun insertOrUpdate(follower: Follower)

}


class FollowerUpsertImpl(private val operations: FluentR2dbcOperations) : FollowerUpsert {
    override suspend fun insertOrUpdate(follower: Follower) {
        val query = Query.query(
            Criteria.where("streamer_id").`is`(follower.streamerId).and(Criteria.where("user_id").`is`(follower.userId))
        )

        val exists = operations.select<Follower>()
            .matching(query)
            .awaitExists()

        if (exists) {
            operations.update<Follower>()
                .matching(query)
                .applyAndAwait(Update.update("scan_number", follower.scanNumber))
        } else {
            operations.insert<Follower>().usingAndAwait(follower)
        }
    }

}

interface FollowerScanScheduleRepository : Repository<FollowerScanSchedule, String>,
    InsertUpdateOperation<FollowerScanSchedule> {

    @Suppress("unused")
    fun findAllByEnabledIsTrue(): Flow<FollowerScanSchedule>

    suspend fun findByStreamerId(streamerId: String): FollowerScanSchedule?

    suspend fun deleteByStreamerId(streamerId: String)
}

interface FollowerScanScheduledTaskRepository : Repository<FollowerScanScheduleTask, Long> {

    fun findAllByStreamerId(streamerId: String): Flow<FollowerScanScheduleTask>


    @RawQuery(
        """
        SELECT id, streamer_id, scheduled_at, status
        FROM follower_scan_schedule_task
        WHERE scheduled_at < :currentTime
        AND status = 'NEW'
        LIMIT 1 FOR UPDATE SKIP LOCKED
        """
    )
    suspend fun findNextNewTask(currentTime: Instant): FollowerScanScheduleTask?

    suspend fun save(task: FollowerScanScheduleTask): FollowerScanScheduleTask

}

interface InsertUpdateOperation<T> {
    suspend fun insert(model: T): T
    suspend fun update(schedule: T): T

}

class InsertUpdateOperationImpl<T : Any>(private val operations: R2dbcEntityOperations) : InsertUpdateOperation<T> {

    override suspend fun insert(model: T): T = operations.insert(model).awaitSingle()

    override suspend fun update(schedule: T): T = operations.update(schedule).awaitSingle()

}

