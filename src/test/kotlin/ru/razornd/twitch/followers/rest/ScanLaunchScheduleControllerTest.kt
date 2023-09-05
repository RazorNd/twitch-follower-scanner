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
import org.springframework.restdocs.payload.PayloadDocumentation.*
import org.springframework.restdocs.webtestclient.WebTestClientRestDocumentation.document
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.*
import org.springframework.test.web.reactive.server.WebTestClient
import ru.razornd.twitch.followers.CreateFollowerScanSchedule
import ru.razornd.twitch.followers.FollowerScanSchedule
import ru.razornd.twitch.followers.UpdateFollowerScanSchedule
import ru.razornd.twitch.followers.configuration.SecurityConfiguration
import ru.razornd.twitch.followers.service.FollowerScanScheduleAlreadyExistsException
import ru.razornd.twitch.followers.service.FollowerScanScheduleNotExistsException
import ru.razornd.twitch.followers.service.FollowerScanScheduleService
import java.time.Instant

@AutoConfigureRestDocs
@Import(SecurityConfiguration::class, RestDocsConfiguration::class)
@WebFluxTest(controllers = [ScanLaunchScheduleController::class])
class ScanLaunchScheduleControllerTest(@Autowired val client: WebTestClient) {

    @MockkBean
    lateinit var service: FollowerScanScheduleService

    private val xsrfHeader = headerWithName("X-XSRF-TOKEN").description("XSRF token")

    private val scheduleFields = listOf(
        fieldWithPath("streamerId").description("ID of the Streamer in Twitch"),
        fieldWithPath("delayHours").description("Time between scan task launches in hours"),
        fieldWithPath("createdAt").description("Schedule creation date"),
        fieldWithPath("endDate").description("Schedule end date"),
        fieldWithPath("enabled").description("schedule enable flag")
    )

    @Test
    fun `should return schedule`() {
        val streamerId = "5566789"

        coEvery { service.getScanSchedule(streamerId) } returns FollowerScanSchedule(
            streamerId,
            24,
            Instant.parse("1972-07-12T11:58:12Z"),
            Instant.parse("2001-04-09T23:59:39Z")
        )

        client.mutateWith(mockOidcLogin().streamerId(streamerId))
            .get()
            .uri("/api/scans/schedule")
            .exchange()
            .expectStatus().isOk
            .expectBody().json(
                """
                {
                  "streamerId": "$streamerId",
                  "delayHours": 24,
                  "createdAt": "1972-07-12T11:58:12Z",
                  "endDate": "2001-04-09T23:59:39Z",
                  "enabled": true
                }                
                """.trimIndent(),
                true
            ).consumeWith(document("scans/schedule/get", responseFields(scheduleFields)))
    }

    @Test
    fun `should return 404 if schedule doesn't exists`() {
        val streamerId = "5567131762"
        val message = "FollowerScanLaunchSchedule for streamer with id='$streamerId' not found"

        coEvery { service.getScanSchedule(streamerId) } throws FollowerScanScheduleNotExistsException(message)

        client.mutateWith(mockOidcLogin().streamerId(streamerId))
            .get()
            .uri("/api/scans/schedule")
            .exchange()
            .expectStatus().isNotFound
            .expectBody().json(
                """
                {
                  "status": 404,
                  "error": "Not Found",
                  "message": "$message"
                }
                """.trimIndent()
            )
    }

    @Test
    fun `should create new schedule`() {
        val streamerId = "119725"
        val createSchedule = CreateFollowerScanSchedule(6, Instant.parse("2024-06-02T23:17:27Z"))
        val response = FollowerScanSchedule(
            streamerId,
            createSchedule.delayHours,
            Instant.parse("2022-07-04T06:05:43Z"),
            createSchedule.endDate
        )


        coEvery { service.createScanSchedule(streamerId, createSchedule) } returns response

        client.mutateWith(mockOidcLogin().streamerId(streamerId))
            .mutateWith(csrf())
            .post()
            .uri("/api/scans/schedule")
            .header("X-XSRF-TOKEN", "e3ed1f89-70d0-47b9-9770-59756dec192a")
            .cookie("SESSION", "7f106b5d-a8eb-43fb-8d2d-87ca0f01e496")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "delayHours": 6,
                  "endDate": "${createSchedule.endDate}"
                }
                """.trimIndent()
            )
            .exchange()
            .expectStatus().isCreated
            .expectBody().json(response.toJson(), true)
            .consumeWith(
                document(
                    "scans/schedule/create",
                    requestFields(scheduleFields.filter { it.path in setOf("delayHours", "endDate") }),
                    requestHeaders(xsrfHeader),
                    responseFields(scheduleFields)
                )
            )
    }

    @Test
    fun `should send error if schedule already exists`() {
        val streamerId = "87824430328"
        val message = "FollowerScanSchedule for streamer with id='$streamerId' already exists"

        coEvery {
            service.createScanSchedule(any(), any())
        } throws FollowerScanScheduleAlreadyExistsException(message)

        client.mutateWith(mockOidcLogin().streamerId(streamerId))
            .mutateWith(csrf())
            .post()
            .uri("/api/scans/schedule")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "delayHours": 12
                }
                """.trimIndent()
            )
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

    @Test
    fun `should update schedule`() {
        val streamerId = "52021660"
        val updateSchedule = UpdateFollowerScanSchedule(24, Instant.parse("1995-04-21T07:45:52Z"), false)
        val response = FollowerScanSchedule(
            streamerId,
            updateSchedule.delayHours!!,
            Instant.parse("2007-05-26T06:01:17Z"),
            updateSchedule.endDate,
            updateSchedule.enabled!!
        )

        coEvery { service.updateScanSchedule(streamerId, updateSchedule) } returns response

        client.mutateWith(mockOidcLogin().streamerId(streamerId))
            .mutateWith(csrf())
            .patch()
            .uri("/api/scans/schedule")
            .header("X-XSRF-TOKEN", "6e327748-9c1d-45cb-9ccd-ff424b43f494")
            .cookie("SESSION", "c508d82a-398e-449f-a0d4-735093ca6f41")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "delayHours": ${updateSchedule.delayHours},
                  "endDate": "${updateSchedule.endDate}",
                  "enabled": ${updateSchedule.enabled}
                }
                """.trimIndent()
            )
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .json(response.toJson(), true)
            .consumeWith(
                document(
                    "scans/schedule/patch",
                    requestFields(scheduleFields.filter { it.path in setOf("delayHours", "endDate", "enabled") }),
                    requestHeaders(xsrfHeader),
                    responseFields(scheduleFields)
                )
            )
    }

    @Test
    fun `should remove schedule`() {
        val streamerId = "78711"

        coEvery { service.deleteScanSchedule(streamerId) } returns Unit

        client.mutateWith(mockOidcLogin().streamerId(streamerId))
            .mutateWith(csrf())
            .delete()
            .uri("/api/scans/schedule")
            .header("X-XSRF-TOKEN", "54b3be9e-1598-436c-910c-d30409c0e514")
            .cookie("SESSION", "3efbff43-f14a-40f2-b74e-e4ac93ead8ba")
            .exchange()
            .expectStatus().isNoContent
            .expectBody()
            .consumeWith(document("scans/schedule/delete", requestHeaders(xsrfHeader)))
    }

    @ParameterizedTest
    @CsvSource("GET", "POST", "PATCH", "DELETE")
    fun `should return 401 if request not authenticate`(method: String) {

        client.mutateWith(csrf())
            .method(HttpMethod.valueOf(method))
            .uri("/api/scans/schedule")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isUnauthorized
    }

    @ParameterizedTest
    @CsvSource("POST", "PATCH", "DELETE")
    fun `should return 403 if modification request without csrf token`(method: String) {

        client.method(HttpMethod.valueOf(method))
            .uri("/api/scans/schedule")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isForbidden
    }

    private fun FollowerScanSchedule.toJson() =
        """
        {
          "streamerId": "$streamerId",
          "delayHours": $delayHours,
          "createdAt": "$createdAt",
          "endDate": "$endDate",
          "enabled": $enabled
        }
        """.trimIndent()

    private fun OidcLoginMutator.streamerId(streamerId: String) = idToken { it.subject(streamerId) }
}
