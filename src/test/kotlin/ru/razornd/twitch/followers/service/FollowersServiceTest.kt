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

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.razornd.twitch.followers.*
import java.time.Instant

class FollowersServiceTest {

    private val followerRepository: FollowerRepository = mockk()

    private val scanRepository: ScanRepository = mockk()

    private val service = FollowersService(followerRepository, scanRepository)

    @Test
    fun `should return followers from repository`() {
        val streamerId = "485802"
        val expected = listOf(
            FollowerDto(true, "989", "Shanda", Instant.parse("2000-09-19T19:23:57Z")),
            FollowerDto(false, "808933", "Collis", Instant.parse("1995-01-20T20:45:14Z")),
            FollowerDto(true, "3353", "Giovani", Instant.parse("2001-06-23T18:57:41Z")),
            FollowerDto(true, "58863", "Shontel", Instant.parse("1973-03-06T17:27:56Z")),
        )

        coEvery { scanRepository.findTopByStreamerIdOrderByScanNumberDesc(streamerId) } returns FollowerScan(
            streamerId,
            5,
            Instant.parse("1974-02-23T23:39:16Z")
        )
        every { followerRepository.findByStreamerId(streamerId) } returns expected.asFlow().map {
            Follower(streamerId, if (!it.unfollowed) 5 else 2, it.userId, it.userName, it.followedAt)
        }


        val actual = runBlocking { service.findFollowers(streamerId) }

        assertThat(actual)
            .usingRecursiveComparison()
            .isEqualTo(expected)
    }

    @Test
    fun `should return an empty collection if there were no scans`() {
        val streamerId = "60015"

        coEvery { scanRepository.findTopByStreamerIdOrderByScanNumberDesc(streamerId) } returns null

        val actual = runBlocking { service.findFollowers(streamerId) }

        assertThat(actual).isEmpty()
        verify(exactly = 0) { followerRepository.findByStreamerId(any()) }
    }
}