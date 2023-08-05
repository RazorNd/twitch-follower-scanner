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

package ru.razornd.twitch.followers.service

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy
import com.fasterxml.jackson.databind.annotation.JsonNaming
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import org.springframework.web.util.UriBuilder
import ru.razornd.twitch.followers.Follower
import ru.razornd.twitch.followers.FollowerRepository
import ru.razornd.twitch.followers.FollowerScan
import ru.razornd.twitch.followers.configuration.TwitchClient
import java.time.Instant

private const val pageSize = 100

@Component
class FollowerScannerOperator(
    @TwitchClient private val webClient: WebClient,
    private val repository: FollowerRepository
) {

    suspend fun scanAndSave(scan: FollowerScan) {
        fetchFollowers(scan).collect { repository.insertOrUpdate(it.toModel(scan)) }
    }

    private fun fetchFollowers(scan: FollowerScan): Flow<FollowerDto> = flow {
        var cursor: String? = null

        do {
            val response = webClient.get()
                .uri {
                    it.path("/channels/followers")
                        .queryParam("broadcaster_id", scan.streamerId)
                        .queryParam("first", pageSize)
                        .queryParamIfPresent("after", cursor)
                        .build()
                }
                .retrieve()
                .awaitBody<PagedResponse<FollowerDto>>()

            response.data.forEach { emit(it) }
            cursor = response.pagination.cursor
        } while (cursor != null)
    }

    private fun FollowerDto.toModel(scan: FollowerScan): Follower = Follower(
        scan.streamerId,
        scan.scanNumber,
        userId,
        userName,
        followedAt
    )
}

private fun UriBuilder.queryParamIfPresent(name: String, value: Any?) = value?.let { queryParam(name, it) } ?: this

@JsonNaming(SnakeCaseStrategy::class)
private data class FollowerDto(
    val userId: String,
    val userName: String,
    val userLogin: String,
    val followedAt: Instant
)

private data class PagedResponse<T>(val total: Int, val data: Collection<T>, val pagination: Pagination)

private data class Pagination(val cursor: String?)
