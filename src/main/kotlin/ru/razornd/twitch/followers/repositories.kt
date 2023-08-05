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
import org.springframework.data.domain.Pageable
import org.springframework.data.r2dbc.core.*
import org.springframework.data.relational.core.query.Criteria
import org.springframework.data.relational.core.query.Query
import org.springframework.data.relational.core.query.Update
import org.springframework.data.repository.Repository

interface ScanRepository : Repository<FollowerScan, Any> {
    fun findByStreamerIdOrderByScanNumberDesc(
        streamerId: String,
        pageable: Pageable = Pageable.unpaged()
    ): Flow<FollowerScan>

    suspend fun findTopByStreamerIdOrderByScanNumberDesc(streamerId: String): FollowerScan?

    suspend fun save(scan: FollowerScan): FollowerScan

}

interface FollowerRepository : Repository<Follower, Any>, FollowerUpsert {
    fun findByStreamerIdAndScanNumber(streamerId: String, scanNumber: Int): Flow<Follower>

    fun findByStreamerIdAndScanNumberLessThan(streamerId: String, scanNumber: Int): Flow<Follower>

}

interface FollowerUpsert {

    suspend fun insertOrUpdate(follower: Follower)

}

@Suppress("unused")
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