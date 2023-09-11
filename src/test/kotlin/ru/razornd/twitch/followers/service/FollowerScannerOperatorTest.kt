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

import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.github.tomakehurst.wiremock.junit5.WireMockExtension
import com.ninjasquad.springmockk.MockkBean
import io.mockk.coVerify
import io.mockk.every
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientService
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.client.registration.InMemoryReactiveClientRegistrationRepository
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.ClientAuthenticationMethod
import org.springframework.security.oauth2.core.OAuth2AccessToken
import org.springframework.security.oauth2.core.OAuth2RefreshToken
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toMono
import ru.razornd.twitch.followers.Follower
import ru.razornd.twitch.followers.FollowerRepository
import ru.razornd.twitch.followers.FollowerScan
import ru.razornd.twitch.followers.configuration.TwitchWebClientConfiguration
import java.time.Duration
import java.time.Instant


@RestClientTest
@Import(
    FollowerScannerOperatorTest.ClientConfiguration::class,
    TwitchWebClientConfiguration::class,
    FollowerScannerOperator::class
)
class FollowerScannerOperatorTest {

    @Autowired
    lateinit var scannerOperator: FollowerScannerOperator

    @MockkBean(relaxUnitFun = true)
    lateinit var repository: FollowerRepository

    @MockkBean
    lateinit var authorizedClientService: ReactiveOAuth2AuthorizedClientService

    @Test
    fun `should fetch followers from twitch api and save it in repository`() {
        val followerScan = FollowerScan("629786", 42, Instant.now())

        @Suppress("ReactiveStreamsUnusedPublisher")
        every {
            authorizedClientService.loadAuthorizedClient<OAuth2AuthorizedClient>(eq("twitch"), any())
        } returns OAuth2AuthorizedClient(
            clientRegistration,
            "streamer",
            OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "THIS_TOKEN_VALUE",
                Instant.now(),
                Instant.now() + Duration.ofHours(3)
            )
        ).toMono()

        stubDefaultResponse(broadcasterId = followerScan.streamerId)

        runBlocking {
            scannerOperator.scanAndSave(followerScan)
        }

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

    @Test
    fun `should use refresh token for get new access token`() {
        val scan = FollowerScan("509902", 7539046, Instant.now())

        @Suppress("ReactiveStreamsUnusedPublisher")
        every {
            authorizedClientService.loadAuthorizedClient<OAuth2AuthorizedClient>(eq("twitch"), any())
        } returns OAuth2AuthorizedClient(
            clientRegistration,
            "streamer",
            OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "THIS_TOKEN_VALUE",
                Instant.now() - Duration.ofHours(4),
                Instant.now() - Duration.ofMinutes(30)
            ),
            OAuth2RefreshToken("REFRESH_TOKEN", Instant.now() - Duration.ofHours(4))
        ).toMono()

        @Suppress("ReactiveStreamsUnusedPublisher")
        every { authorizedClientService.saveAuthorizedClient(any(), any()) } returns Mono.just(true).then()

        stubFor(
            post("/token")
                .withRequestBody(containing("grant_type=refresh_token"))
                .withRequestBody(containing("refresh_token=REFRESH_TOKEN"))
                .withRequestBody(containing("client_id=${clientRegistration.clientId}"))
                .withRequestBody(containing("client_secret=${clientRegistration.clientSecret}"))
                .willReturn(okJson("""
                    {
                      "access_token": "REFRESHED_TOKEN",
                      "expires_in": 13697,
                      "refresh_token": "NEW_REFRESH_TOKEN",
                      "scope": [
                        "moderator:read:followers",
                        "openid"
                      ],
                      "token_type": "bearer"
                    }
                """.trimIndent()))
        )
        stubDefaultResponse(scan.streamerId, "Bearer REFRESHED_TOKEN")

        runBlocking { scannerOperator.scanAndSave(scan) }

        coVerify {
            repository.insertOrUpdate(
                Follower(
                    scan.streamerId,
                    scan.scanNumber,
                    "11111",
                    "UserDisplayName",
                    Instant.parse("2022-05-24T22:22:08Z")
                )
            )
        }
    }

    private fun stubDefaultResponse(broadcasterId: String, authorization: String = "Bearer THIS_TOKEN_VALUE") =
        stubFor(
            get(urlPathEqualTo("/helix/channels/followers"))
                .withQueryParam("broadcaster_id", equalTo(broadcasterId))
                .withHeader("Authorization", equalTo(authorization))
                .willReturn(
                    okJson(
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
        )


    @Configuration
    open class ClientConfiguration {

        @Bean
        open fun inMemoryClientRegistration(): InMemoryReactiveClientRegistrationRepository {
            return InMemoryReactiveClientRegistrationRepository(clientRegistration)
        }
    }

    companion object {

        @JvmField
        @RegisterExtension
        val server: WireMockExtension = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .configureStaticDsl(true)
            .build()

        @JvmStatic
        private lateinit var clientRegistration: ClientRegistration

        @BeforeAll
        @JvmStatic
        fun createClientRegistration() {
            clientRegistration = ClientRegistration.withRegistrationId("twitch")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .clientId("test-client-id")
                .clientSecret("test-client-secret")
                .redirectUri("/redirect")
                .authorizationUri(server.url("/authorize"))
                .tokenUri(server.url("/token"))
                .build()
        }

        @DynamicPropertySource
        @JvmStatic
        fun baseUrlDynamicPropertySource(registry: DynamicPropertyRegistry) {
            registry.add("twitch.client.base-url") { server.url("/helix") }
        }
    }
}
