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

package ru.razornd.twitch.followers.rest

import com.ninjasquad.springmockk.MockkBean
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf
import org.springframework.test.web.reactive.server.WebTestClient
import ru.razornd.twitch.followers.FollowerScan
import ru.razornd.twitch.followers.configuration.SecurityConfiguration
import ru.razornd.twitch.followers.service.ScanService
import java.time.Instant

@WebFluxTest(controllers = [ScanController::class])
@Import(SecurityConfiguration::class)
@MockKExtension.CheckUnnecessaryStub
class ScanControllerTest(@Autowired val webClient: WebTestClient) {

    @MockkBean
    lateinit var scanService: ScanService

    @Test
    fun `should return list of scans`() {
        coEvery { scanService.findScans(streamerId = any()) } returns listOf(
            FollowerScan("currentStreamer()", 1, Instant.parse("2023-04-12T03:29:29Z")),
            FollowerScan("currentStreamer()", 2, Instant.parse("2023-04-13T01:22:21Z")),
            FollowerScan("currentStreamer()", 3, Instant.parse("2023-04-14T21:30:48Z")),
            FollowerScan("currentStreamer()", 4, Instant.parse("2023-04-15T22:22:24Z")),
        )

        webClient.get().uri("/api/scans")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk
            .expectBody().json(
                """
                [
                  {
                    "streamerId": "currentStreamer()",
                    "scanNumber": 1,
                    "createdAt": "2023-04-12T03:29:29Z"
                  },
                  {
                    "streamerId": "currentStreamer()",
                    "scanNumber": 2,
                    "createdAt": "2023-04-13T01:22:21Z"
                  },
                  {
                    "streamerId": "currentStreamer()",
                    "scanNumber": 3,
                    "createdAt": "2023-04-14T21:30:48Z"
                  },
                  {
                    "streamerId": "currentStreamer()",
                    "scanNumber": 4,
                    "createdAt": "2023-04-15T22:22:24Z"
                  }
                ]
            """.trimIndent()
            )

        coVerify { scanService.findScans(streamerId = "currentStreamer()") }
    }

    @Test
    fun `should start new scan`() {
        coEvery { scanService.startScan(streamerId = "currentStreamer()") } returns FollowerScan(
            "currentStreamer()",
            966,
            Instant.parse("2023-08-04T16:27:00Z")
        )

        webClient.mutateWith(csrf())
            .post()
            .uri("/api/scans")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isCreated
            .expectBody().json(
                """
                {
                  "streamerId": "currentStreamer()",
                  "scanNumber": 966,
                  "createdAt": "2023-08-04T16:27:00Z"
                }
            """.trimIndent()
            )
    }
}