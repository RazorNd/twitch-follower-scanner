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
import org.junit.jupiter.api.Test
import ru.razornd.twitch.followers.FollowerScan
import ru.razornd.twitch.followers.FollowerScanScheduleTask
import ru.razornd.twitch.followers.scheduled.FollowerScanTaskRunner
import java.time.Instant

class FollowerScanTaskRunnerTest {

    private val scheduleService: FollowerScanScheduleService = mockk()

    private val scanService: ScanService = mockk()

    private val runner = FollowerScanTaskRunner(scheduleService, scanService)

    @Test
    fun executeAvailableTasks() {
        val task = FollowerScanScheduleTask(
            streamerId = "837953359",
            scheduledAt = Instant.parse("2017-06-28T05:57:25Z")
        )

        var count = 0

        coEvery { scheduleService.tryRunNextSchedule(any()) } coAnswers {
            if (count++ < 5) {
                firstArg<suspend (FollowerScanScheduleTask) -> Unit>()(task)
                true
            } else false
        }
        coEvery { scanService.startScan(any()) } answers {
            val streamerId = firstArg<String>()
            FollowerScan(streamerId, 42, Instant.now())
        }

        runner.executeAvailableTasks()

        coVerify(exactly = 5) { scanService.startScan(task.streamerId) }
    }
}