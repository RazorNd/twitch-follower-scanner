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

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.ResponseStatus
import ru.razornd.twitch.followers.*
import java.time.Clock
import java.time.Duration
import java.time.Instant

@Service
class FollowerScanScheduleService(
    private val scheduleRepository: FollowerScanScheduleRepository,
    private val taskRepository: FollowerScanScheduledTaskRepository
) {
    var clock: Clock = Clock.systemUTC()

    @Throws(FollowerScanScheduleNotExistsException::class)
    suspend fun getScanSchedule(streamerId: String): FollowerScanSchedule {
        return scheduleRepository.findByStreamerId(streamerId) ?: scheduleNotExists(streamerId)
    }

    @Throws(FollowerScanScheduleAlreadyExistsException::class)
    suspend fun createScanSchedule(
        streamerId: String,
        createDto: CreateFollowerScanSchedule
    ): FollowerScanSchedule {
        return scheduleRepository.insert(FollowerScanSchedule(streamerId, createDto))
            .also { taskRepository.save(it.createFirstTask()) }
    }

    @Throws(FollowerScanScheduleNotExistsException::class)
    suspend fun updateScanSchedule(
        streamerId: String,
        updateDto: UpdateFollowerScanSchedule
    ): FollowerScanSchedule {
        val schedule = scheduleRepository.findByStreamerId(streamerId) ?: scheduleNotExists(streamerId)

        val updated = schedule.update(updateDto)

        return scheduleRepository.update(updated)
    }

    suspend fun deleteScanSchedule(streamerId: String) {
        scheduleRepository.deleteByStreamerId(streamerId)
    }

    private fun scheduleNotExists(streamerId: String): Nothing {
        throw FollowerScanScheduleNotExistsException("Schedule for streamer with id='$streamerId' doesn't exists")
    }

    private fun FollowerScanSchedule.update(updateDto: UpdateFollowerScanSchedule) = copy(
        delayHours = updateDto.delayHours ?: delayHours,
        endDate = updateDto.endDate ?: endDate,
        enabled = updateDto.enabled ?: enabled
    )

    private fun FollowerScanSchedule(
        streamerId: String,
        createDto: CreateFollowerScanSchedule
    ) = FollowerScanSchedule(
        streamerId,
        createDto.delayHours,
        Instant.now(clock),
        createDto.endDate
    )

    private fun FollowerScanSchedule.createFirstTask() = FollowerScanScheduleTask(
        streamerId = streamerId,
        scheduledAt = clock.instant() + delayDuration
    )

    private val FollowerScanSchedule.delayDuration: Duration get() = Duration.ofHours(delayHours.toLong())
}

@ResponseStatus(HttpStatus.NOT_FOUND)
class FollowerScanScheduleNotExistsException(message: String) : RuntimeException(message)

@ResponseStatus(HttpStatus.CONFLICT)
class FollowerScanScheduleAlreadyExistsException(message: String) : RuntimeException(message)
