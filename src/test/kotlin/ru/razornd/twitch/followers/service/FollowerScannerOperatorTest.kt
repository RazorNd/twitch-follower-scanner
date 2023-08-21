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
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.reactor.asCoroutineContext
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders.CONTENT_TYPE
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientService
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.client.registration.InMemoryReactiveClientRegistrationRepository
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.web.server.ServerWebExchange
import reactor.kotlin.core.publisher.toMono
import reactor.util.context.Context
import ru.razornd.twitch.followers.Follower
import ru.razornd.twitch.followers.FollowerRepository
import ru.razornd.twitch.followers.FollowerScan
import ru.razornd.twitch.followers.configuration.TwitchWebClientConfiguration
import java.time.Instant

private val clientRegistration = ClientRegistration.withRegistrationId("twitch")
    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
    .clientId("test-client-id")
    .redirectUri("/redirect")
    .authorizationUri("https://id.server/authorize")
    .tokenUri("https://id.server/token")
    .build()

@RestClientTest
@ContextConfiguration(
    classes = [
        FollowerScannerOperatorTest.ClientConfiguration::class,
        TwitchWebClientConfiguration::class,
        FollowerScannerOperator::class
    ]
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
        } returns OAuth2AuthorizedClient(clientRegistration, "streamer", mockk(relaxed = true)).toMono()

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

        val webExchange = MockServerWebExchange.from(MockServerHttpRequest.get("/"))
        runBlocking(Context.of(ServerWebExchange::class.java, webExchange).asCoroutineContext()) {
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

    @Configuration
    open class ClientConfiguration {

        @Bean
        open fun inMemoryClientRegistration(): InMemoryReactiveClientRegistrationRepository {
            return InMemoryReactiveClientRegistrationRepository(clientRegistration)
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
