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

package ru.razornd.twitch.followers.security.support

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.codec.DecoderHttpMessageReader
import org.springframework.http.codec.json.Jackson2JsonDecoder
import org.springframework.http.server.reactive.ServerHttpResponse
import org.springframework.mock.http.client.reactive.MockClientHttpResponse
import org.springframework.web.reactive.function.BodyExtractor
import java.util.*

class OAuth2AccessTokenResponseBodyExtractorTest {

    private val context = object : BodyExtractor.Context {
        override fun messageReaders() = listOf(DecoderHttpMessageReader(Jackson2JsonDecoder()))

        override fun serverResponse(): Optional<ServerHttpResponse> = Optional.empty()

        override fun hints(): Map<String, Any> = emptyMap()
    }

    private val bodyExtractor = OAuth2AccessTokenResponseBodyExtractor()

    @MethodSource("arguments")
    @ParameterizedTest
    fun `parse twitch token response`(body: String) {
        val inputMessage = MockClientHttpResponse(HttpStatus.OK)
        inputMessage.headers.contentType = MediaType.APPLICATION_JSON
        inputMessage.setBody(body)

        val response = bodyExtractor.extract(inputMessage, context).block()

        assertThat(response).isNotNull
    }

    companion object {
        @JvmStatic
        fun arguments(): Collection<String> {
            //language=JSON
            return listOf(
                """
                {
                  "access_token": "access_token_value",
                  "refresh_token": "refresh_token_value",
                  "scope": [
                    "openid",
                    "user:read:follows"
                  ],
                  "id_token": "id_token_value",
                  "token_type": "bearer",
                  "expires_in": 13446,
                  "nonce": "nonce_value"
                }
                """.trimIndent(),
                """
                {
                    "access_token": "access_token_value",
                    "refresh_token": "refresh_token_value",
                    "id_token": "id_token_value",
                    "token_type": "bearer",
                    "expires_in": 13446,
                    "nonce": "nonce_value"
                }
                """.trimIndent(),
                """
                {
                    "access_token": "access_token_value",
                    "refresh_token": "refresh_token_value",
                    "scope":  "openid user:read:follows",
                    "id_token": "id_token_value",
                    "token_type": "bearer",
                    "expires_in": 13446,
                    "nonce": "nonce_value"
                }
                """.trimIndent(),
            )
        }
    }
}