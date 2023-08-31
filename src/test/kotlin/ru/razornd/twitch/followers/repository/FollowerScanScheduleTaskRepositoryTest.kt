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

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.db.type.Changes
import org.assertj.db.type.Source
import org.assertj.db.type.Table
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.r2dbc.DataR2dbcTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import ru.razornd.twitch.followers.FollowerScanScheduleTask
import ru.razornd.twitch.followers.FollowerScanScheduleTask.Status
import ru.razornd.twitch.followers.FollowerScanScheduledTaskRepository
import ru.razornd.twitch.followers.captureChanges
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import org.assertj.db.api.Assertions.assertThat as assertDb


@DataR2dbcTest
@Testcontainers(disabledWithoutDocker = true)
class FollowerScanScheduleTaskRepositoryTest(@Autowired val repository: FollowerScanScheduledTaskRepository) {

    private val changes = Changes(
        Table(Source(postgres.jdbcUrl, postgres.username, postgres.password), "follower_scan_schedule_task")
    )

    @Test
    fun `save new`() {
        val task = FollowerScanScheduleTask(
            streamerId = "4310",
            scheduledAt = Instant.parse("2008-08-15T16:37:36Z")
        )
        val actual = changes.captureChanges { runBlocking { repository.save(task) } }

        assertThat(actual)
            .describedAs("save return model")
            .hasNoNullFieldsOrProperties()

        assertDb(changes)
            .hasNumberOfChanges(1)
            .change().isCreation
            .rowAtEndPoint()
            .value("id").isNotNull
            .value("streamer_id").isEqualTo(task.streamerId)
            .value("scheduled_at").isEqualTo(Timestamp.from(task.scheduledAt))
            .value("status").isEqualTo("NEW")
    }

    @Test
    fun `save update`() {
        val task = FollowerScanScheduleTask(
            id = 9747338278L,
            streamerId = "735806",
            scheduledAt = Instant.parse("2024-04-25T04:03:42Z"),
            status = Status.COMPLETED
        )

        val actual = changes.captureChanges { runBlocking { repository.save(task) } }

        assertThat(actual).describedAs("save return model").isEqualTo(task)

        assertDb(changes)
            .hasNumberOfChanges(1)
            .change().isModification
            .rowAtEndPoint()
            .value("id").isEqualTo(task.id)
            .value("streamer_id").isEqualTo(task.streamerId)
            .value("scheduled_at").isEqualTo(Timestamp.from(task.scheduledAt))
            .value("status").isEqualTo(task.status.name)
    }

    @Test
    fun findAllByStreamerId() {
        val streamerId = "6473513"

        val actual = runBlocking { repository.findAllByStreamerId(streamerId).toList() }

        assertThat(actual)
            .usingRecursiveComparison()
            .isEqualTo((0L until 5L).map {
                FollowerScanScheduleTask(
                    id = 9747338279 + it,
                    streamerId = streamerId,
                    scheduledAt = Instant.parse("2022-12-02T17:12:42Z") + Duration.ofDays(it),
                    status = if (it != 4L) Status.COMPLETED else Status.NEW
                )
            })
    }

    @Test
    fun findNextNewTask() {
        val currentTime = Instant.parse("2020-03-30T12:00:00Z")

        val actual = runBlocking { repository.findNextNewTask(currentTime) }


        Thread {
            val parallel = runBlocking {
                repository.findNextNewTask(currentTime)
            }

            assertThat(parallel).describedAs("parallel request").isNull()
        }.join()

        assertThat(actual)
            .describedAs("next task")
            .usingRecursiveComparison()
            .isEqualTo(FollowerScanScheduleTask(9747338285, "26426", Instant.parse("2020-03-30T06:44:32Z"), Status.NEW))
    }

    companion object {

        @Container
        @ServiceConnection
        val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:15-alpine"))
    }
}