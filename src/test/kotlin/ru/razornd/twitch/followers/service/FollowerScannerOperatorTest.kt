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

import com.ninjasquad.springmockk.MockkBean
import io.mockk.coVerify
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest
import org.springframework.http.HttpHeaders.CONTENT_TYPE
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import ru.razornd.twitch.followers.Follower
import ru.razornd.twitch.followers.FollowerRepository
import ru.razornd.twitch.followers.FollowerScan
import ru.razornd.twitch.followers.configuration.TwitchWebClientConfiguration
import java.time.Instant

@RestClientTest(components = [FollowerScannerOperator::class, TwitchWebClientConfiguration::class])
class FollowerScannerOperatorTest {

    @Autowired
    lateinit var scannerOperator: FollowerScannerOperator

    @MockkBean(relaxUnitFun = true)
    lateinit var repository: FollowerRepository

    @Test
    fun `should fetch followers from twitch api and save it in repository`() {
        val followerScan = FollowerScan("629786", 42, Instant.now())
        server.enqueue(
            MockResponse()
                .setHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
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

        runBlocking { scannerOperator.scanAndSave(followerScan) }

        coVerify {
            repository.insertOrUpdate(
                Follower(
                    followerScan.streamerId,
                    followerScan.scanNumber,
                    "11111",
                    "UserDisplayName",
                    Instant.parse("2022-05-24T22:22:08Z")
                )
            )
        }
    }

    companion object {
        val server = MockWebServer()

        @JvmStatic
        @AfterAll
        fun stopServer() {
            server.close()
        }

        @DynamicPropertySource
        @JvmStatic
        fun baseUrlDynamicPropertySource(registry: DynamicPropertyRegistry) {
            registry.add("twitch.client.base-url") { server.url("/helix/").toString() }
        }
    }
}