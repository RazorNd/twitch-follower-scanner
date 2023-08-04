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
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.razornd.twitch.followers.FollowerScan
import ru.razornd.twitch.followers.ScanRepository
import java.time.Instant


class ScanServiceTest {

    private val repository = mockk<ScanRepository>()

    private val service = ScanService(repository)

    @Test
    fun `should fetch scan from repository`() {
        val streamerId = "804067"
        val expected = FollowerScan(streamerId, 1, Instant.parse("2023-08-04T17:10:00Z"))

        coEvery { repository.findByStreamerIdOrderByScanNumberDesc(any()) } returns flowOf(expected)

        val scans = runBlocking { service.findScans(streamerId) }

        assertThat(scans).containsOnly(expected)
    }
}