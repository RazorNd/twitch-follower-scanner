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

package ru.razornd.twitch.followers

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant

@Table
data class FollowerScan(
    val streamerId: String,
    val scanNumber: Int,
    val createdAt: Instant
)

@Table
data class Follower(
    val streamerId: String,
    val scanNumber: Int,
    val userId: String,
    val userName: String,
    val followedAt: Instant
)

@Table
data class FollowerScanSchedule(
    @Id
    val streamerId: String,
    val delayHours: Int,
    val createdAt: Instant,
    val endDate: Instant?,
    val enabled: Boolean = true
)

@Table
data class FollowerScanScheduleTask(
    @Id
    val id: Long? = null,
    val streamerId: String,
    val scheduledAt: Instant,
    val status: Status = Status.NEW
) {
    enum class Status {
        NEW, COMPLETED
    }
}