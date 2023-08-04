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
import org.springframework.stereotype.Service
import ru.razornd.twitch.followers.FollowerScan
import ru.razornd.twitch.followers.ScanRepository

@Service
class ScanService(private val scanRepository: ScanRepository) {
    suspend fun findScans(streamerId: String): Collection<FollowerScan> {
        return scanRepository.findByStreamerIdOrderByScanNumberDesc(streamerId).toList()
    }

    suspend fun startScan(streamerId: String): FollowerScan {
        TODO("Not yet implemented")
    }

}
