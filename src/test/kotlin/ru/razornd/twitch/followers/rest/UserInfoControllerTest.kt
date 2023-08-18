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

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.context.annotation.Import
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.responseFields
import org.springframework.restdocs.webtestclient.WebTestClientRestDocumentation.document
import org.springframework.security.oauth2.core.oidc.StandardClaimNames
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockOidcLogin
import org.springframework.test.web.reactive.server.WebTestClient
import ru.razornd.twitch.followers.configuration.SecurityConfiguration

@AutoConfigureRestDocs
@Import(RestDocsConfiguration::class)
@WebFluxTest(controllers = [UserInfoController::class, SecurityConfiguration::class])
class UserInfoControllerTest(@Autowired val client: WebTestClient) {

    @Test
    fun `should return information from oidc token`() {
        val userId = "2030521"
        val name = "natikafy"
        val picture = "https://pictures.deb/584186b3-07eb-4e10-882d-d35e2df62538"
        client.mutateWith(mockOidcLogin().idToken { idToken ->
            idToken.subject(userId)
            idToken.claims {
                it[StandardClaimNames.PREFERRED_USERNAME] = name
                it[StandardClaimNames.PICTURE] = picture
            }
        })
            .get().uri("/api/user-info")
            .exchange()
            .expectStatus().isOk
            .expectBody().json(
                """
                {
                  "id": "$userId",
                  "name": "$name",
                  "picture": "$picture"
                }
                """.trimIndent()
            ).consumeWith(
                document(
                    "user-info/get",
                    responseFields(
                        fieldWithPath("id").description("ID of the current user on Twitch"),
                        fieldWithPath("name").description("Name of the current user on Twitch"),
                        fieldWithPath("picture").description("User Image URL on Twitch")
                    )
                )
            )
    }

    @Test
    fun `should return 401 if not authenticated`() {
        client.get()
            .uri("/api/user-info")
            .exchange()
            .expectStatus().isUnauthorized
    }

}