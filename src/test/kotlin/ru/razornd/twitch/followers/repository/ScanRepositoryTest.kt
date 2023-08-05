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
import org.assertj.core.api.Assertions
import org.assertj.db.type.Changes
import org.assertj.db.type.Source
import org.assertj.db.type.Table
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.r2dbc.DataR2dbcTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.data.domain.Pageable
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import ru.razornd.twitch.followers.FollowerScan
import ru.razornd.twitch.followers.ScanRepository
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@DataR2dbcTest(properties = ["spring.sql.init.mode=always"])
@Testcontainers(disabledWithoutDocker = true)
class ScanRepositoryTest {

    @Autowired
    lateinit var repository: ScanRepository

    private val table = Table(Source(postgres.jdbcUrl, postgres.username, postgres.password), "follower_scan")

    private val changes = Changes(table)

    @Test
    fun `should find scan for streamer`() {
        val streamerId = "685499"
        val scans = runBlocking { repository.findByStreamerIdOrderByScanNumberDesc(streamerId).toList() }

        Assertions.assertThat(scans)
            .usingRecursiveComparison()
            .isEqualTo((1..5).reversed().map {
                FollowerScan(streamerId, it, LocalDate.of(2023, 8, it).atTime(18, 59).toInstant(ZoneOffset.UTC))
            })
    }

    @Test
    fun `should find scan by page`() {
        val streamerId = "913613"

        val scans = runBlocking {
            repository.findByStreamerIdOrderByScanNumberDesc(streamerId, Pageable.ofSize(3)).toList()
        }

        Assertions.assertThat(scans)
            .usingRecursiveComparison()
            .isEqualTo((3..5).reversed().map {
                FollowerScan(streamerId, it, LocalDate.of(2023, 8, it).atTime(18, 59).toInstant(ZoneOffset.UTC))
            })
    }

    @Test
    fun `should save entity`() {
        val scan = FollowerScan("2252", 1, Instant.now())

        changes.setStartPointNow()
        Assertions.assertThat(runBlocking { repository.save(scan) }).hasNoNullFieldsOrProperties()
        changes.setEndPointNow()


        org.assertj.db.api.Assertions.assertThat(changes)
            .hasNumberOfChanges(1)
            .change().isCreation
            .rowAtEndPoint()
            .value("streamer_id").isEqualTo(scan.streamerId)
            .value("scan_number").isEqualTo(scan.scanNumber)
            .value("created_at").isEqualTo(Timestamp.from(scan.createdAt))
    }

    companion object {

        @Container
        @ServiceConnection
        val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:15-alpine"))
    }
}