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

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.razornd.twitch.followers.Follower
import ru.razornd.twitch.followers.FollowerDto
import ru.razornd.twitch.followers.FollowerRepository
import ru.razornd.twitch.followers.ScanRepository

@Service
open class FollowersService(
    private val followerRepository: FollowerRepository,
    private val scanRepository: ScanRepository
) {
    @Transactional(readOnly = true)
    open suspend fun findFollowers(streamerId: String): Collection<FollowerDto> {
        val actualScanNumber = actualScanNumber(streamerId) ?: return emptyList()
        return followerRepository.findByStreamerId(streamerId).map { it.asDto(actualScanNumber) }.toList()
    }

    private suspend fun actualScanNumber(streamerId: String): Int? =
        scanRepository.findTopByStreamerIdOrderByScanNumberDesc(streamerId)?.scanNumber

    private fun Follower.asDto(actualScanNumber: Int): FollowerDto =
        FollowerDto(scanNumber != actualScanNumber, userId, userName, followedAt)
}
