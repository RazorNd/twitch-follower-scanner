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

import kotlinx.coroutines.flow.toList
import org.springframework.dao.CannotAcquireLockException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.ResponseStatus
import ru.razornd.twitch.followers.FollowerScan
import ru.razornd.twitch.followers.ScanRepository
import java.time.Clock
import java.time.Instant

@Service
open class ScanService(private val repository: ScanRepository, private val operator: FollowerScannerOperator) {

    var clock: Clock = Clock.systemDefaultZone()

    @Transactional(readOnly = true)
    open suspend fun findScans(streamerId: String): Collection<FollowerScan> {
        return repository.findByStreamerIdOrderByScanNumberDesc(streamerId).toList()
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    open suspend fun startScan(streamerId: String): FollowerScan {
        try {
            val scan = repository.findTopByStreamerIdOrderByScanNumberDesc(streamerId)?.nextScan()
                ?: FollowerScan(streamerId)

            operator.scanAndSave(scan)

            return scan.let { repository.save(it) }
        } catch (e: CannotAcquireLockException) {
            throw ParallelScanTask("Execute parallel scan task for streamerId='$streamerId'", e)
        }
    }

    private fun FollowerScan(streamerId: String) = FollowerScan(streamerId, 1, Instant.now(clock))

    private fun FollowerScan.nextScan() = copy(scanNumber = scanNumber + 1, createdAt = Instant.now(clock))
}

@ResponseStatus(HttpStatus.CONFLICT)
class ParallelScanTask(message: String, cause: Throwable? = null) : RuntimeException(message, cause)