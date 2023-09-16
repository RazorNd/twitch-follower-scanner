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
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.restdocs.headers.HeaderDocumentation.headerWithName
import org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders
import org.springframework.restdocs.operation.preprocess.Preprocessors.modifyHeaders
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.responseFields
import org.springframework.restdocs.webtestclient.WebTestClientRestDocumentation.document
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockOidcLogin
import org.springframework.test.web.reactive.server.WebTestClient
import ru.razornd.twitch.followers.FollowerScan
import ru.razornd.twitch.followers.configuration.SecurityConfiguration
import ru.razornd.twitch.followers.service.ParallelScanTask
import ru.razornd.twitch.followers.service.ScanService
import java.time.Instant

@WebFluxTest(
    controllers = [ScanController::class],
    properties = ["logging.level.org.springframework.security.web.server=debug"]
)
@AutoConfigureRestDocs
@Import(SecurityConfiguration::class, RestDocsConfiguration::class)
@MockKExtension.CheckUnnecessaryStub
class ScanControllerTest(@Autowired val webClient: WebTestClient) {

    private val scanFields = listOf(
        fieldWithPath("streamerId").description("ID of the Streamer in Twitch"),
        fieldWithPath("scanNumber").description("Scan sequence number"),
        fieldWithPath("createdAt").description("Scan date")
    )

    @MockkBean(relaxed = true)
    lateinit var scanService: ScanService

    @Test
    fun `should return list of scans`() {
        val streamerId = "787101"
        coEvery { scanService.findScans(streamerId = any()) } answers {
            listOf(
                FollowerScan(firstArg(), 1, Instant.parse("2023-04-12T03:29:29Z")),
                FollowerScan(firstArg(), 2, Instant.parse("2023-04-13T01:22:21Z")),
                FollowerScan(firstArg(), 3, Instant.parse("2023-04-14T21:30:48Z")),
                FollowerScan(firstArg(), 4, Instant.parse("2023-04-15T22:22:24Z")),
            )
        }

        webClient.mutateWith(mockOidcLogin().idToken { it.subject(streamerId) })
            .get().uri("/api/scans")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk
            .expectBody().json(
                """
                [
                  {
                    "streamerId": "$streamerId",
                    "scanNumber": 1,
                    "createdAt": "2023-04-12T03:29:29Z"
                  },
                  {
                    "streamerId": "$streamerId",
                    "scanNumber": 2,
                    "createdAt": "2023-04-13T01:22:21Z"
                  },
                  {
                    "streamerId": "$streamerId",
                    "scanNumber": 3,
                    "createdAt": "2023-04-14T21:30:48Z"
                  },
                  {
                    "streamerId": "$streamerId",
                    "scanNumber": 4,
                    "createdAt": "2023-04-15T22:22:24Z"
                  }
                ]
            """.trimIndent()
            ).consumeWith(document("scans/list", responseFields().andWithPrefix("[].", scanFields)))

        coVerify { scanService.findScans(streamerId) }
    }

    @Test
    fun `should start new scan`() {
        val streamerId = "686606"
        coEvery { scanService.startScan(any()) } answers {
            FollowerScan(
                firstArg(),
                966,
                Instant.parse("2023-08-04T16:27:00Z")
            )
        }


        webClient.mutateWith(csrf())
            .mutateWith(mockOidcLogin().idToken { it.subject(streamerId) })
            .post()
            .uri("/api/scans")
            .header("X-XSRF-TOKEN", "247ec92d-2824-4422-bcb2-409bb6c50cea")
            .cookie("SESSION", "1197bf5f-ba2e-440a-96eb-82f8a8cae146")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isCreated
            .expectBody().json(
                """
                {
                  "streamerId": "$streamerId",
                  "scanNumber": 966,
                  "createdAt": "2023-08-04T16:27:00Z"
                }
            """.trimIndent()
            ).consumeWith(
                document(
                    "scans/create",
                    preprocessRequest(modifyHeaders().remove("Content-Type")),
                    responseFields(scanFields),
                    requestHeaders(headerWithName("X-XSRF-TOKEN").description("XSRF token"))
                )
            )
    }

    @ParameterizedTest
    @CsvSource(
        "GET, /api/scans",
        "POST, /api/scans"
    )
    fun `unauthenticated request should return 401`(method: String, uri: String) {
        webClient.mutateWith(csrf())
            .method(HttpMethod.valueOf(method))
            .uri(uri)
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `parallel scan task should return 409 Conflict`() {
        val message = "Parallel scan"
        coEvery { scanService.startScan(any()) } throws ParallelScanTask(message)

        webClient.mutateWith(csrf())
            .mutateWith(mockOidcLogin().idToken { it.subject("1743712921") })
            .post()
            .uri("/api/scans")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.CONFLICT)
            .expectBody().json(
                """
                {
                  "status": 409,
                  "error": "Conflict",
                  "message": "$message"
                }
                """.trimIndent()
            )
    }
}