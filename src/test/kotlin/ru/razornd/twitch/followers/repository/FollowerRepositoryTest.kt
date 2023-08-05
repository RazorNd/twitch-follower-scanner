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
import org.assertj.db.api.Assertions
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
import ru.razornd.twitch.followers.Follower
import ru.razornd.twitch.followers.FollowerRepository
import java.sql.Timestamp
import java.time.Instant

@DataR2dbcTest(properties = ["spring.sql.init.mode=always", "logging.level.io.r2dbc.postgresql.QUERY=debug"])
@Testcontainers(disabledWithoutDocker = true)
class FollowerRepositoryTest {

    @Autowired
    lateinit var repository: FollowerRepository

    private val table = Table(Source(postgres.jdbcUrl, postgres.username, postgres.password), "follower")
    private val changes = Changes(table)

    @Test
    fun `should update follower if it exists`() {
        val follower = Follower(
            "482d897d-5de4-43d8-8f26-d6c72545b01a",
            2,
            "11111",
            "UserDisplayName",
            Instant.parse("2022-05-24T22:22:08Z")
        )

        changes.setStartPointNow()
        runBlocking { repository.insertOrUpdate(follower) }
        changes.setEndPointNow()

        Assertions.assertThat(changes)
            .hasNumberOfChanges(1)
            .change().isModification
            .hasNumberOfModifiedColumns(1)
            .hasModifiedColumns("scan_number")
            .rowAtEndPoint()
            .value("scan_number").isEqualTo(follower.scanNumber)
    }

    @Test
    fun `should insert follower if it doesn't exists`() {
        val follower = Follower(
            "43020057",
            1,
            "1297",
            "TestUser",
            Instant.parse("2022-10-29T08:35:08Z")
        )

        changes.setStartPointNow()
        runBlocking { repository.insertOrUpdate(follower) }
        changes.setEndPointNow()

        Assertions.assertThat(changes)
            .hasNumberOfChanges(1)
            .change().isCreation
            .rowAtEndPoint()
            .value("streamer_id").isEqualTo(follower.streamerId)
            .value("scan_number").isEqualTo(follower.scanNumber)
            .value("user_id").isEqualTo(follower.userId)
            .value("user_name").isEqualTo(follower.userName)
            .value("followed_at").isEqualTo(Timestamp.from(follower.followedAt))
    }

    @Test
    fun `should find follower with scan number`() {
        val actual = runBlocking {
            repository.findByStreamerIdAndScanNumber("913613", 1).toList()
        }

        org.assertj.core.api.Assertions.assertThat(actual)
            .containsOnly(
                Follower("913613", 1, "10035", "joyce.gusikowski", Instant.parse("2022-08-31T11:27:48.222Z")),
                Follower("913613", 1, "42480", "timmy.gusikowski", Instant.parse("2022-08-11T05:44:19.693Z")),
                Follower("913613", 1, "89687", "yadira.hagenes", Instant.parse("2023-03-20T22:10:07.373Z"))
            )
    }

    @Test
    fun `should find follower with scan number less than`() {
        val actual = runBlocking {
            repository.findByStreamerIdAndScanNumberLessThan("913613", 1).toList()
        }

        org.assertj.core.api.Assertions.assertThat(actual)
            .containsOnly(
                Follower("913613", 0, "08297", "leslee.white", Instant.parse("2023-06-09T21:23:09.553Z")),
                Follower("913613", 0, "21660", "marline.leannon", Instant.parse("2023-06-08T21:02:23.734Z")),
                Follower("913613", 0, "23761", "emmett.greenholt", Instant.parse("2023-06-12T12:46:52.833Z")),
                Follower("913613", 0, "29786", "norberto.king", Instant.parse("2023-02-06T08:36:23.072Z")),
                Follower("913613", 0, "32047", "zaida.okon", Instant.parse("2023-07-20T04:27:08.917Z")),
                Follower("913613", 0, "46413", "migdalia.dooley", Instant.parse("2023-02-11T15:53:58.713Z")),
                Follower("913613", 0, "65873", "guillermo.nicolas", Instant.parse("2022-10-09T12:58:00.236Z"))
            )
    }

    companion object {

        @Container
        @ServiceConnection
        val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:15-alpine"))
    }
}