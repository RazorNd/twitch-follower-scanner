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
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import ru.razornd.twitch.followers.*
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

class FollowerScanScheduleServiceTest {

    private val currentTime = Instant.parse("2023-03-06T17:04:24Z")

    private val scheduleRepository: FollowerScanScheduleRepository = mockk {
        coEvery { insert(any()) } answers { firstArg() }
        coEvery { update(any()) } answers { firstArg() }
        coEvery { deleteByStreamerId(any()) } returns Unit
    }

    private val taskRepository: FollowerScanScheduledTaskRepository = mockk {
        coEvery { save(any()) } answers { firstArg() }
    }

    private val service = FollowerScanScheduleService(scheduleRepository, taskRepository).apply {
        clock = Clock.fixed(currentTime, ZoneId.systemDefault())
    }

    @Test
    fun getScanSchedule() {
        val streamerId = "2198530"
        val expected = FollowerScanSchedule(
            streamerId,
            3,
            Instant.parse("2022-11-05T04:57:29Z"),
            null
        )

        coEvery { scheduleRepository.findByStreamerId(streamerId) } returns expected

        val actual = runBlocking { service.getScanSchedule(streamerId) }

        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun `getScanSchedule not exists`() {
        val streamerId = "97286"

        coEvery { scheduleRepository.findByStreamerId(streamerId) } returns null

        assertThatThrownBy { runBlocking { service.getScanSchedule(streamerId) } }
            .isExactlyInstanceOf(FollowerScanScheduleNotExistsException::class.java)
            .hasMessage("Schedule for streamer with id='$streamerId' doesn't exists")
    }

    @Test
    fun createScanSchedule() {
        val streamerId = "9908580"
        val createDto = CreateFollowerScanSchedule(4, Instant.parse("2070-07-30T09:30:34Z"))
        val expected = FollowerScanSchedule(
            streamerId,
            createDto.delayHours,
            currentTime,
            createDto.endDate
        )

        val actual = runBlocking { service.createScanSchedule(streamerId, createDto) }

        assertThat(actual)
            .usingRecursiveComparison()
            .isEqualTo(expected)

        coVerify { scheduleRepository.insert(expected) }
        coVerify {
            taskRepository.save(
                FollowerScanScheduleTask(
                    streamerId = streamerId,
                    scheduledAt = currentTime + expected.delayDuration
                )
            )
        }
    }

    @Test
    fun updateScanSchedule() {
        val streamerId = "20420189"
        val updateDto = UpdateFollowerScanSchedule(5, Instant.parse("1981-08-04T11:36:42Z"), false)
        val exists = FollowerScanSchedule(
            streamerId,
            3,
            Instant.parse("1974-03-13T11:52:44Z"),
            null,
            true
        )
        val expected = exists.copy(
            delayHours = updateDto.delayHours!!,
            endDate = updateDto.endDate,
            enabled = updateDto.enabled!!
        )

        coEvery { scheduleRepository.findByStreamerId(streamerId) } returns exists


        val actual = runBlocking { service.updateScanSchedule(streamerId, updateDto) }

        assertThat(actual)
            .describedAs("Updated entity")
            .usingRecursiveComparison()
            .isEqualTo(expected)

        coVerify { scheduleRepository.update(expected) }
    }

    @ParameterizedTest
    @CsvSource(
        "6, , ",
        " , 1981-08-04T11:36:42Z, ",
        " , , false",
        "6, 1981-08-04T11:36:42Z, ",
        "6, , false",
        " , 1981-08-04T11:36:42Z, false",
        "6, , true",
        "2, , ",
    )
    fun `updateScanSchedule partial`(delayHours: Int?, endDate: Instant?, enabled: Boolean?) {
        val streamerId = "20420189"
        val exists = FollowerScanSchedule(
            streamerId,
            3,
            Instant.parse("1974-03-13T11:52:44Z"),
            null,
            true
        )
        val expected = exists.copy(
            delayHours = delayHours ?: exists.delayHours,
            endDate = endDate ?: exists.endDate,
            enabled = enabled ?: exists.enabled
        )

        coEvery { scheduleRepository.findByStreamerId(streamerId) } returns exists


        val actual = runBlocking {
            service.updateScanSchedule(streamerId, UpdateFollowerScanSchedule(delayHours, endDate, enabled))
        }

        assertThat(actual)
            .describedAs("Updated entity")
            .usingRecursiveComparison()
            .isEqualTo(expected)

        coVerify { scheduleRepository.update(expected) }
    }

    @Test
    fun `updateScanSchedule not exists`() {
        val streamerId = "90172"

        coEvery { scheduleRepository.findByStreamerId(streamerId) } returns null

        assertThatThrownBy {
            runBlocking {
                service.updateScanSchedule(streamerId, UpdateFollowerScanSchedule(null, null, false))
            }
        }.isExactlyInstanceOf(FollowerScanScheduleNotExistsException::class.java)
            .hasMessage("Schedule for streamer with id='$streamerId' doesn't exists")
    }

    @Test
    fun deleteScanSchedule() {
        val streamerId = "46038"

        runBlocking { service.deleteScanSchedule(streamerId) }

        coVerify { scheduleRepository.deleteByStreamerId(streamerId) }
    }

    @Test
    fun tryRunNextSchedule() {
        val callback = mockk<(FollowerScanScheduleTask) -> Unit>(relaxed = true)
        val streamerId = "588435838"
        val expected = FollowerScanScheduleTask(
            id = 281816,
            streamerId = streamerId,
            scheduledAt = Instant.parse("1975-05-16T21:11:44Z"),
            status = FollowerScanScheduleTask.Status.NEW
        )
        val schedule = FollowerScanSchedule(
            streamerId = streamerId,
            delayHours = 6,
            createdAt = Instant.parse("1996-04-19T02:15:08Z"),
            endDate = null
        )

        coEvery { taskRepository.findNextNewTask(currentTime) } returns expected
        coEvery { scheduleRepository.findByStreamerId(streamerId) } returns schedule

        val actual = runBlocking { service.tryRunNextSchedule(callback) }

        assertThat(actual).describedAs("response").isTrue()

        verify { callback(expected) }
        coVerify { taskRepository.save(expected.copy(status = FollowerScanScheduleTask.Status.COMPLETED)) }
        coVerify {
            taskRepository.save(
                expected.copy(
                    id = null,
                    scheduledAt = currentTime + Duration.ofHours(schedule.delayHours.toLong())
                )
            )
        }
    }

    @Test
    fun `tryRunNextSchedule task not found`() {
        val callback = mockk<(FollowerScanScheduleTask) -> Unit>(relaxed = true)

        coEvery { taskRepository.findNextNewTask(currentTime) } returns null

        val actual = runBlocking { service.tryRunNextSchedule(callback) }

        assertThat(actual).describedAs("response").isFalse()
    }

    @Test
    fun `tryRunNextSchedule schedule not exists`() {
        val streamerId = "576410"
        val task = FollowerScanScheduleTask(
            id = 8481482,
            streamerId = streamerId,
            scheduledAt = Instant.parse("1975-05-16T21:11:44Z"),
            status = FollowerScanScheduleTask.Status.NEW
        )

        coEvery { taskRepository.findNextNewTask(any()) } returns task
        coEvery { scheduleRepository.findByStreamerId(streamerId) } returns null

        runBlocking { service.tryRunNextSchedule { } }
        coVerify(exactly = 1) { taskRepository.save(any()) }
    }

    @Test
    fun `tryRunNextSchedule schedule not enabled`() {
        val streamerId = "576410"
        val task = FollowerScanScheduleTask(
            id = 8481482,
            streamerId = streamerId,
            scheduledAt = Instant.parse("1975-05-16T21:11:44Z"),
            status = FollowerScanScheduleTask.Status.NEW
        )

        coEvery { taskRepository.findNextNewTask(any()) } returns task
        coEvery { scheduleRepository.findByStreamerId(streamerId) } returns FollowerScanSchedule(
            streamerId = streamerId,
            delayHours = 12,
            createdAt = Instant.parse("2003-07-23T02:45:50Z"),
            endDate = null,
            enabled = false
        )

        runBlocking { service.tryRunNextSchedule { } }
        coVerify(exactly = 1) { taskRepository.save(any()) }
    }

    @Test
    fun `tryRunNextSchedule schedule not active`() {
        val streamerId = "576410"
        val task = FollowerScanScheduleTask(
            id = 8481482,
            streamerId = streamerId,
            scheduledAt = Instant.parse("1975-05-16T21:11:44Z"),
            status = FollowerScanScheduleTask.Status.NEW
        )

        coEvery { taskRepository.findNextNewTask(any()) } returns task
        coEvery { scheduleRepository.findByStreamerId(streamerId) } returns FollowerScanSchedule(
            streamerId = streamerId,
            delayHours = 12,
            createdAt = Instant.parse("2003-07-23T02:45:50Z"),
            endDate = currentTime - Duration.ofDays(3),
            enabled = true
        )

        runBlocking { service.tryRunNextSchedule { } }
        coVerify(exactly = 1) { taskRepository.save(any()) }
    }

    private val FollowerScanSchedule.delayDuration get() = Duration.ofHours(delayHours.toLong())
}