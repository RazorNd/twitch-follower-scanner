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

package ru.razornd.twitch.followers.scheduled

import kotlinx.coroutines.reactor.asCoroutineContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.ReactiveSecurityContextHolder.withAuthentication
import org.springframework.stereotype.Component
import ru.razornd.twitch.followers.FollowerScanScheduleTask
import ru.razornd.twitch.followers.service.FollowerScanScheduleService
import ru.razornd.twitch.followers.service.ScanService
import java.util.concurrent.TimeUnit

@Component
class FollowerScanTaskRunner(
    private val scheduleService: FollowerScanScheduleService,
    private val scanService: ScanService
) {
    @Scheduled(fixedDelay = 5, timeUnit = TimeUnit.MINUTES)
    fun executeAvailableTasks() = runBlocking {
        do {
            val completed = scheduleService.tryRunNextSchedule {
                withContext(withAuthentication(it.authentication).asCoroutineContext()) {
                    scanService.startScan(it.streamerId)
                }
            }
        } while (completed)
    }

    private val FollowerScanScheduleTask.authentication: Authentication get() = ScanTaskAuthentication(this)

    class ScanTaskAuthentication(private val task: FollowerScanScheduleTask) : AbstractAuthenticationToken(listOf()) {
        override fun getCredentials(): Nothing = error("Doesn't have credentials")

        override fun getPrincipal(): String = task.streamerId

    }
}