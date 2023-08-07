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
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames
import org.springframework.security.oauth2.core.oidc.OidcIdToken
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.security.web.server.context.WebSessionServerSecurityContextRepository.DEFAULT_SPRING_SECURITY_CONTEXT_ATTR_NAME
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.server.WebSession
import org.springframework.web.server.session.DefaultWebSessionManager
import org.springframework.web.server.session.WebSessionManager
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.time.Instant
import org.assertj.db.api.Assertions.assertThat as assertDb

private const val XSRF = "6c436860-17b5-4e07-987e-b7bb2eef159b"

private const val SALT_XSRF =
    "Fpx4NG4-KdRoupImV-Umyp2pzJlIRBwmhe9rNeoKo2Ms7t_IIP9MB1gGH-RFi6VEYsgSr62e4aBwc3kL59gJV9hvxgUd2-aq"

@SpringBootTest(webEnvironment = RANDOM_PORT, properties = ["spring.sql.init.mode=always"])
@Testcontainers(disabledWithoutDocker = true)
class FollowersScannerApplicationTests {

    private val followerTable = Table(Source(postgres.jdbcUrl, postgres.username, postgres.password), "follower")

    private val followersChanges = Changes(followerTable)

    private val clientRegistration = mockk<ClientRegistration>(relaxed = true) {
        every { registrationId } returns "twitch"
    }

    @Autowired
    lateinit var sessionManager: WebSessionManager

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

    private fun ReactiveOAuth2AuthorizedClientService.authorizeMockClient(
        user: Any,
        clientRegistration: ClientRegistration
    ) {
        saveAuthorizedClient(
            OAuth2AuthorizedClient(
                clientRegistration,
                "streamer",
                mockk(relaxed = true)
            ), TestingAuthenticationToken(user, null)
        ).block()
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
