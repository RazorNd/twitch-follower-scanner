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
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.dao.CannotAcquireLockException
import ru.razornd.twitch.followers.FollowerScan
import ru.razornd.twitch.followers.ScanRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneId


class ScanServiceTest {

    private val repository = mockk<ScanRepository> {
        coEvery { save(any()) } answers { firstArg() }
    }

    private val operator = mockk<FollowerScannerOperator>(relaxUnitFun = true)

    private val currentTime = Instant.parse("2023-08-04T22:09:00Z")

    private val service = ScanService(repository, operator).apply {
        clock = Clock.fixed(currentTime, ZoneId.systemDefault())
    }

    @Test
    fun `should fetch scan from repository`() {
        val streamerId = "804067"
        val expected = FollowerScan(streamerId, 1, Instant.parse("2023-08-04T17:10:00Z"))

        coEvery { repository.findByStreamerIdOrderByScanNumberDesc(any()) } returns flowOf(expected)

        val scans = runBlocking { service.findScans(streamerId) }

        assertThat(scans).containsOnly(expected)
    }

    @Test
    fun `should create scan entity and start scan operation`() {
        val streamerId = "23002802"
        val expected = FollowerScan(streamerId, 6867, currentTime)

        coEvery { repository.findTopByStreamerIdOrderByScanNumberDesc(streamerId) } returns FollowerScan(
            streamerId,
            6866,
            Instant.parse("2023-08-04T22:02:00Z")
        )

        runBlocking { service.startScan(streamerId) }

        coVerify { repository.save(expected) }
        coVerify { operator.scanAndSave(expected) }
    }

    @Test
    fun `should throw ParallelScanException if can't acquire lock`() {
        val streamerId = "2700212"
        val expectedCause = CannotAcquireLockException("could not serialize access due to concurrent update")

        coEvery { repository.findTopByStreamerIdOrderByScanNumberDesc(streamerId) } returns FollowerScan(
            streamerId,
            760,
            Instant.parse("2023-08-28T19:29:22Z")
        )

        coEvery { operator.scanAndSave(any()) } throws expectedCause

        assertThatThrownBy { runBlocking { service.startScan(streamerId) } }
            .isExactlyInstanceOf(ParallelScanTask::class.java)
            .hasMessage("Execute parallel scan task for streamerId='$streamerId'")
            .hasCause(expectedCause)
    }
}