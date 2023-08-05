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

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.db.type.Changes
import org.assertj.db.type.Source
import org.assertj.db.type.Table
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import org.assertj.db.api.Assertions.assertThat as assertDb

private const val XSRF = "6c436860-17b5-4e07-987e-b7bb2eef159b"

private const val SALT_XSRF =
    "Fpx4NG4-KdRoupImV-Umyp2pzJlIRBwmhe9rNeoKo2Ms7t_IIP9MB1gGH-RFi6VEYsgSr62e4aBwc3kL59gJV9hvxgUd2-aq"

@SpringBootTest(webEnvironment = RANDOM_PORT, properties = ["spring.sql.init.mode=always"])
@Testcontainers(disabledWithoutDocker = true)
class FollowersScannerApplicationTests {


    private val followerTable = Table(Source(postgres.jdbcUrl, postgres.username, postgres.password), "follower")

    private val followersChanges = Changes(followerTable)

    @Test
    fun `scan followers`(@Autowired client: WebTestClient) {
        server.enqueue(
            MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(
                    """
                    {
                      "total": 1,
                      "data": [
                        {
                          "user_id": "11111",
                          "user_name": "UserDisplayName",
                          "user_login": "userloginname",
                          "followed_at": "2022-05-24T22:22:08Z"
                        }
                      ],
                      "pagination": {}
                    }
                    """.trimIndent()
                )
        )

        followersChanges.setStartPointNow()
        client.post()
            .uri("/api/scans")
            .cookie("XSRF-TOKEN", XSRF)
            .header("X-XSRF-TOKEN", SALT_XSRF)
            .exchange()
            .expectStatus().isCreated
        followersChanges.setEndPointNow()

        assertDb(followersChanges)
            .hasNumberOfChanges(1)
            .change().isCreation
            .rowAtEndPoint()
            .value("streamer_id")
    }

    companion object {
        @Container
        @ServiceConnection
        val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:15-alpine"))

        val server = MockWebServer()

        @JvmStatic
        @AfterAll
        fun stopServer() {
            server.close()
        }

        @DynamicPropertySource
        @JvmStatic
        fun twitchUrlDynamicPropertySource(registry: DynamicPropertyRegistry) {
            registry.add("twitch.client.base-url") { server.url("/helix/").toString() }
        }
    }

}
