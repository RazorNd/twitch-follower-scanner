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
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.context.annotation.Import
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.responseFields
import org.springframework.restdocs.webtestclient.WebTestClientRestDocumentation.document
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockOidcLogin
import org.springframework.test.web.reactive.server.WebTestClient
import ru.razornd.twitch.followers.FollowerDto
import ru.razornd.twitch.followers.configuration.SecurityConfiguration
import ru.razornd.twitch.followers.service.FollowersService
import java.time.Instant

@AutoConfigureRestDocs
@Import(RestDocsConfiguration::class)
@WebFluxTest(controllers = [FollowersController::class, SecurityConfiguration::class])
class FollowersControllerTest {

    @Autowired
    lateinit var client: WebTestClient

    @MockkBean
    lateinit var service: FollowersService

    @Test
    fun `should return all followers`() {
        val streamerId = "3379806"

        coEvery { service.findFollowers(streamerId) } returns listOf(
            FollowerDto(
                unfollowed = true,
                userId = "03356",
                userName = "Kaleem",
                followedAt = Instant.parse("2018-01-04T10:51:52Z")
            ),
            FollowerDto(
                unfollowed = false,
                userId = "1742726",
                userName = "Alxis",
                followedAt = Instant.parse("2010-04-28T22:00:49Z")
            ),
            FollowerDto(
                unfollowed = false,
                userId = "3598381",
                userName = "Deondrae",
                followedAt = Instant.parse("1987-04-04T00:06:52Z")
            )
        )

        client.mutateWith(mockOidcLogin().idToken { it.subject(streamerId) })
            .get().uri("/api/followers")
            .exchange()
            .expectStatus().isOk
            .expectBody().json(
                """
                [
                  {
                    "unfollowed": true,
                    "userId": "03356",
                    "userName": "Kaleem",
                    "followedAt": "2018-01-04T10:51:52Z"
                  },
                  {
                    "unfollowed": false,
                    "userId": "1742726",
                    "userName": "Alxis",
                    "followedAt": "2010-04-28T22:00:49Z"
                  },
                  {
                    "unfollowed": false,
                    "userId": "3598381",
                    "userName": "Deondrae",
                    "followedAt": "1987-04-04T00:06:52Z"
                  }
                ]     
                """.trimIndent()
            ).consumeWith(
                document(
                    "followers/list", responseFields(
                        fieldWithPath("[].userId").description("ID of the User in Twitch"),
                        fieldWithPath("[].userName").description("Name of the User in Twitch"),
                        fieldWithPath("[].followedAt").description("Date from which the user subscribed"),
                        fieldWithPath("[].unfollowed").description("Indication that the user has unsubscribed")
                    )
                ))
    }
}