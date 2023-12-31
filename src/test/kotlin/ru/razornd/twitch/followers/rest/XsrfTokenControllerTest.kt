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
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockOidcLogin
import org.springframework.test.web.reactive.server.WebTestClient
import ru.razornd.twitch.followers.configuration.SecurityConfiguration

@AutoConfigureRestDocs
@Import(RestDocsConfiguration::class)
@WebFluxTest(controllers = [XsrfTokenController::class, SecurityConfiguration::class])
internal class XsrfTokenControllerTest {

    @Autowired
    lateinit var client: WebTestClient

    @Test
    fun `get xsrf token`() {
        client.mutateWith(mockOidcLogin())
            .get().uri("/api/xsrf-token")
            .exchange()
            .expectCookie().httpOnly("XSRF-TOKEN", true)
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.token").exists()
            .jsonPath("$.headerName").isEqualTo("X-XSRF-TOKEN")
            .consumeWith(
                document(
                    "xsrf/get",
                    responseFields(
                        fieldWithPath("token").description("XSRF token"),
                        fieldWithPath("headerName").description("Header name where to pass xsrf token")
                    )
                )
            )
    }
}