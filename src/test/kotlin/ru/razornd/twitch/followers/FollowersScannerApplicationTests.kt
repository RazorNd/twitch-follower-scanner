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

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.db.type.Changes
import org.assertj.db.type.Source
import org.assertj.db.type.Table
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.context.SecurityContextImpl
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientService
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.core.OAuth2AccessToken
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames
import org.springframework.security.oauth2.core.oidc.OidcIdToken
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.security.web.server.context.WebSessionServerSecurityContextRepository.DEFAULT_SPRING_SECURITY_CONTEXT_ATTR_NAME
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBodyList
import org.springframework.web.server.WebSession
import org.springframework.web.server.session.DefaultWebSessionManager
import org.springframework.web.server.session.WebSessionManager
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import ru.razornd.twitch.followers.service.FollowerScanScheduleService
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import org.assertj.db.api.Assertions.assertThat as assertDb

private const val XSRF = "6c436860-17b5-4e07-987e-b7bb2eef159b"

private const val SALT_XSRF =
    "Fpx4NG4-KdRoupImV-Umyp2pzJlIRBwmhe9rNeoKo2Ms7t_IIP9MB1gGH-RFi6VEYsgSr62e4aBwc3kL59gJV9hvxgUd2-aq"

@SpringBootTest(webEnvironment = RANDOM_PORT, properties = ["spring.sql.init.mode=always"])
@Testcontainers(disabledWithoutDocker = true)
class FollowersScannerApplicationTests {

    private val source = Source(postgres.jdbcUrl, postgres.username, postgres.password)

    private val followersChanges = Changes(Table(source, "follower"))

    private val scheduleChanges = Changes(Table(source, "follower_scan_schedule"))

    private val scheduleTaskChanges = Changes(Table(source, "follower_scan_schedule_task"))

    private val clientRegistration = mockk<ClientRegistration>(relaxed = true) {
        every { registrationId } returns "twitch"
    }

    @Autowired
    lateinit var sessionManager: WebSessionManager

    @BeforeEach
    fun setUp(@Autowired scheduleService: FollowerScanScheduleService) {
        scheduleService.clock = Clock.fixed(Instant.parse("2020-01-01T12:00:00Z"), ZoneId.systemDefault())
    }

    @Test
    fun `scan followers`(
        @Autowired client: WebTestClient,
        @Autowired authClientService: ReactiveOAuth2AuthorizedClientService
    ) {
        val streamerId = "3096988"
        val user = DefaultOidcUser(
            listOf(),
            OidcIdToken(
                "token",
                Instant.parse("2023-08-06T19:49:00Z"),
                Instant.parse("2023-08-06T19:50:00Z"),
                mapOf(IdTokenClaimNames.SUB to streamerId)
            )
        )
        val session = createOAuthSession(user)
        authClientService.authorizeMockClient(user, clientRegistration)

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
            .cookie("SESSION", session.id)
            .header("X-XSRF-TOKEN", SALT_XSRF)
            .exchange()
            .expectStatus().isCreated
        followersChanges.setEndPointNow()

        assertThat(server.takeRequest().requestUrl)
            .describedAs("Request to twitch")
            .extracting({ it?.encodedPath }, { it?.queryParameter("broadcaster_id") })
            .containsExactly("/helix/channels/followers", streamerId)

        assertDb(followersChanges)
            .hasNumberOfChanges(1)
            .change().isCreation
            .rowAtEndPoint()
            .value("streamer_id")
    }

    @Test
    fun `show followers`(@Autowired client: WebTestClient) {
        val streamerId = "913613"
        val session = createOAuthSessionWithDefaultUser(streamerId)

        client.get().uri("/api/followers")
            .cookie("SESSION", session.id)
            .exchange()
            .expectStatus().isOk
            .expectBodyList<FollowerDto>()
            .hasSize(100)
            .contains(
                FollowerDto(false, "36514", "werner.sanford", Instant.parse("2023-01-02T21:56:13.027Z")),
                FollowerDto(true, "29786", "norberto.king", Instant.parse("2023-02-06T08:36:23.072Z")),
                FollowerDto(true, "22249", "jerry.muller", Instant.parse("2022-12-18T11:41:24.411Z")),
                FollowerDto(false, "02993", "foster.wilkinson", Instant.parse("2022-08-27T13:51:59.515Z")),
            )

    }

    @Test
    fun `create scan schedule`(@Autowired client: WebTestClient) {
        val streamerId = "88049"
        val session = createOAuthSessionWithDefaultUser(streamerId)

        listOf(scheduleChanges, scheduleTaskChanges).captureChanges {
            client.post()
                .uri("/api/scans/schedule")
                .cookie("XSRF-TOKEN", XSRF)
                .cookie("SESSION", session.id)
                .header("X-XSRF-TOKEN", SALT_XSRF)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(
                    """
                    {
                      "delayHours": 12
                    }
                    """.trimIndent()
                )
                .exchange()
                .expectStatus().isCreated
                .expectBody().json(
                    """
                    {
                      "streamerId": "$streamerId",
                      "delayHours": 12,
                      "endDate": null,
                      "enabled": true
                    }
                    """.trimIndent()
                )
        }


        assertDb(scheduleChanges)
            .hasNumberOfChanges(1)
            .change().isCreation
            .rowAtEndPoint()
            .value("streamer_id").isEqualTo(streamerId)
            .value("delay_hours").isEqualTo(12)

        assertDb(scheduleTaskChanges)
            .hasNumberOfChanges(1)
            .change().isCreation
            .rowAtEndPoint()
            .value("streamer_id").isEqualTo(streamerId)
            .value("scheduled_at").isNotNull
            .value("status").isEqualTo("NEW")
    }

    @Test
    fun `update scan schedule`(@Autowired client: WebTestClient) {
        val streamerId = "166583"
        val session = createOAuthSessionWithDefaultUser(streamerId)

        scheduleChanges.setStartPointNow()
        client.patch()
            .uri("/api/scans/schedule")
            .cookie("XSRF-TOKEN", XSRF)
            .cookie("SESSION", session.id)
            .header("X-XSRF-TOKEN", SALT_XSRF)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "delayHours": 12
                }
                """.trimIndent()
            )
            .exchange()
            .expectStatus().isOk
            .expectBody().json(
                """
                {
                  "streamerId": "$streamerId",
                  "delayHours": 12,
                  "endDate": null,
                  "enabled": true
                }
                """.trimIndent()
            )
        scheduleChanges.setEndPointNow()

        assertDb(scheduleChanges)
            .hasNumberOfChanges(1)
            .change().isModification
            .rowAtEndPoint()
            .value("delay_hours").isEqualTo(12)
    }

    @Test
    fun `get scan schedule`(@Autowired client: WebTestClient) {
        val streamerId = "41745269"
        val session = createOAuthSessionWithDefaultUser(streamerId)


        client.get()
            .uri("/api/scans/schedule")
            .cookie("SESSION", session.id)
            .exchange()
            .expectStatus().isOk
            .expectBody().json(
                """
                {
                  "streamerId": "$streamerId",
                  "delayHours": 3,
                  "createdAt": "2023-08-28T03:25:42Z",
                  "endDate": null,
                  "enabled": true
                }
                """.trimIndent()
            )
    }

    private fun ReactiveOAuth2AuthorizedClientService.authorizeMockClient(
        user: Any,
        clientRegistration: ClientRegistration
    ) {
        saveAuthorizedClient(
            OAuth2AuthorizedClient(
                clientRegistration,
                "streamer",
                OAuth2AccessToken(
                    OAuth2AccessToken.TokenType.BEARER,
                    "token",
                    Instant.parse("1978-06-07T07:22:54Z"),
                    Instant.parse("2013-09-28T22:50:09Z")
                )
            ), TestingAuthenticationToken(user, null)
        ).block()
    }

    private fun createOAuthSessionWithDefaultUser(streamerId: String): WebSession {
        val user = DefaultOidcUser(
            listOf(),
            OidcIdToken(
                "token",
                Instant.parse("2023-08-06T19:49:00Z"),
                Instant.parse("2023-08-06T19:50:00Z"),
                mapOf(IdTokenClaimNames.SUB to streamerId)
            )
        )
        return createOAuthSession(user)
    }

    private fun createOAuthSession(user: OAuth2User): WebSession = createWebSession { session ->
        val authenticationToken = OAuth2AuthenticationToken(user, listOf(), "twitch")
        session.attributes[DEFAULT_SPRING_SECURITY_CONTEXT_ATTR_NAME] = SecurityContextImpl(authenticationToken)
    }

    private fun createWebSession(builder: (WebSession) -> Unit = {}): WebSession = runBlocking {
        (sessionManager as DefaultWebSessionManager).sessionStore.createWebSession()
            .awaitSingle()
            .apply(builder)
            .also { it.save().block() }
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
