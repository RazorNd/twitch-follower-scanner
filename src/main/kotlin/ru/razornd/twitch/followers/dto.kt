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

import java.time.Instant

data class FollowerDto(
    val unfollowed: Boolean,
    val userId: String,
    val userName: String,
    val followedAt: Instant
)

data class UserInfo(
    val id: String,
    val name: String,
    val picture: String
)

data class CreateFollowerScanSchedule(
    val delayHours: Int,
    val endDate: Instant?
)

data class UpdateFollowerScanSchedule(
    val delayHours: Int?,
    val endDate: Instant?,
    val enabled: Boolean?
)