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

package ru.razornd.twitch.followers.repository

import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.db.type.Changes
import org.assertj.db.type.Source
import org.assertj.db.type.Table
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.r2dbc.DataR2dbcTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.dao.DuplicateKeyException
import org.springframework.dao.TransientDataAccessResourceException
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import ru.razornd.twitch.followers.FollowerScanSchedule
import ru.razornd.twitch.followers.FollowerScanScheduleRepository
import java.sql.Timestamp
import java.time.Instant
import org.assertj.db.api.Assertions.assertThat as assertDb

@DataR2dbcTest
@Testcontainers(disabledWithoutDocker = true)
class FollowerScanScheduleRepositoryTest {

    @Autowired
    lateinit var repository: FollowerScanScheduleRepository

    private val table = Table(Source(postgres.jdbcUrl, postgres.username, postgres.password), "follower_scan_schedule")
    private val changes = Changes(table)

    @Test
    fun insert() {
        val schedule = FollowerScanSchedule(
            "192185",
            6,
            Instant.parse("1993-09-25T21:05:06Z"),
            Instant.parse("1990-10-08T07:16:20Z")
        )

        changes.setStartPointNow()
        runBlocking { repository.insert(schedule) }
        changes.setEndPointNow()

        assertDb(changes)
            .hasNumberOfChanges(1)
            .change().isCreation
            .rowAtEndPoint()
            .value("streamer_id").isEqualTo(schedule.streamerId)
            .value("delay_hours").isEqualTo(schedule.delayHours)
            .value("created_at").isEqualTo(Timestamp.from(schedule.createdAt))
            .value("end_date").isEqualTo(Timestamp.from(schedule.endDate))
            .value("enabled").isEqualTo(schedule.enabled)
    }

    @Test
    fun `insert duplicate`() {
        val schedule = FollowerScanSchedule(
            "166583",
            3,
            Instant.parse("2023-08-28T22:21:00Z"),
            null,
            false
        )

        assertThatThrownBy { runBlocking { repository.insert(schedule) } }
            .isExactlyInstanceOf(DuplicateKeyException::class.java)
    }

    @Test
    fun update() {
        val schedule = FollowerScanSchedule(
            "166583",
            3,
            Instant.parse("2023-08-28T22:21:00Z"),
            null,
            false
        )

        changes.setStartPointNow()
        runBlocking { repository.update(schedule) }
        changes.setEndPointNow()

        assertDb(changes)
            .hasNumberOfChanges(1)
            .change().isModification
            .rowAtEndPoint()
            .value("streamer_id").isEqualTo(schedule.streamerId)
            .value("delay_hours").isEqualTo(schedule.delayHours)
            .value("created_at").isEqualTo(Timestamp.from(schedule.createdAt))
            .value("end_date").isNull
            .value("enabled").isEqualTo(schedule.enabled)
    }

    @Test
    fun `update doesn't exists`() {
        val schedule = FollowerScanSchedule(
            "598015",
            3,
            Instant.parse("2023-08-26T14:11:00Z"),
            null,
            true
        )

        assertThatThrownBy { runBlocking { repository.update(schedule) } }
            .isExactlyInstanceOf(TransientDataAccessResourceException::class.java)
    }

    @Test
    fun findByStreamerId() {
        val streamerId = "8522738"
        val actual = runBlocking { repository.findByStreamerId(streamerId) }

        assertThat(actual)
            .isNotNull
            .usingRecursiveComparison()
            .isEqualTo(
                FollowerScanSchedule(
                    streamerId,
                    12,
                    Instant.parse("2023-08-28T09:01:45Z"),
                    Instant.parse("2024-08-28T09:01:45Z"),
                    false
                )
            )
    }

    @Test
    fun `findByStreamerId doesn't exists`() {
        val streamerId = "067648"
        val actual = runBlocking { repository.findByStreamerId(streamerId) }

        assertThat(actual).isNull()
    }

    @Test
    fun deleteByStreamerId() {
        changes.setStartPointNow()
        runBlocking { repository.deleteByStreamerId("41745269") }
        changes.setEndPointNow()

        assertDb(changes)
            .hasNumberOfChanges(1)
            .change().isDeletion
    }

    companion object {

        @Container
        @ServiceConnection
        val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:15-alpine"))
    }
}