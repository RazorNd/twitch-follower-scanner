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

import com.nimbusds.oauth2.sdk.*
import com.nimbusds.oauth2.sdk.token.AccessTokenType
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.reactor.mono
import net.minidev.json.JSONObject
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.ReactiveHttpInputMessage
import org.springframework.security.oauth2.core.OAuth2AccessToken
import org.springframework.security.oauth2.core.OAuth2AuthorizationException
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2ErrorCodes
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse
import org.springframework.web.reactive.function.BodyExtractor
import org.springframework.web.reactive.function.BodyExtractors
import reactor.core.publisher.Mono

class OAuth2AccessTokenResponseBodyExtractor :
    BodyExtractor<Mono<OAuth2AccessTokenResponse>, ReactiveHttpInputMessage> {
    override fun extract(
        inputMessage: ReactiveHttpInputMessage,
        context: BodyExtractor.Context
    ): Mono<OAuth2AccessTokenResponse> = mono {
        val json = extractJson(inputMessage, context)
            ?: throw OAuth2AuthorizationException(invalidTokenResponse("Empty OAuth 2.0 Access Token Response"))

        val tokenResponse = parse(json)
        val accessTokenResponse = oauth2AccessTokenResponse(tokenResponse)
        oauth2AccessTokenResponse(accessTokenResponse)
    }

    private suspend fun extractJson(
        inputMessage: ReactiveHttpInputMessage,
        context: BodyExtractor.Context
    ): MutableMap<String, Any?>? {
        val delegate = BodyExtractors.toMono(STRING_OBJECT_MAP)

        return try {
            delegate.extract(inputMessage, context).awaitSingleOrNull()
        } catch (ex: Throwable) {
            throw OAuth2AuthorizationException(
                invalidTokenResponse("An error occurred parsing the Access Token response: " + ex.message),
                ex
            )
        }
    }

    private fun parse(json: MutableMap<String, Any?>): TokenResponse {
        return try {
            fixScopes(json)
            TokenResponse.parse(JSONObject(json))
        } catch (ex: ParseException) {
            throw OAuth2AuthorizationException(
                invalidTokenResponse("An error occurred parsing the Access Token response: " + ex.message),
                ex
            )
        }
    }

    private fun fixScopes(json: MutableMap<String, Any?>) {
        val scopes = json["scope"]
        if (scopes is Collection<*>) {
            json["scope"] = scopes.joinToString(" ")
        }
    }

    private fun oauth2AccessTokenResponse(tokenResponse: TokenResponse): AccessTokenResponse {
        if (tokenResponse.indicatesSuccess()) {
            return tokenResponse as AccessTokenResponse
        }
        val tokenErrorResponse = tokenResponse as TokenErrorResponse
        throw OAuth2AuthorizationException(tokenErrorResponse.errorObject.getOAuth2Error())
    }

    private fun ErrorObject?.getOAuth2Error(): OAuth2Error = when {
        this == null -> OAuth2Error(OAuth2ErrorCodes.SERVER_ERROR)
        else -> OAuth2Error(
            code ?: OAuth2ErrorCodes.SERVER_ERROR,
            description,
            uri?.toString()
        )
    }


    private fun invalidTokenResponse(message: String) = OAuth2Error(INVALID_TOKEN_RESPONSE_ERROR_CODE, message, null)

    private fun oauth2AccessTokenResponse(accessTokenResponse: AccessTokenResponse): OAuth2AccessTokenResponse {
        val accessToken = accessTokenResponse.tokens.accessToken

        return OAuth2AccessTokenResponse.withToken(accessToken.value)
            .tokenType(accessToken.type.tokenType())
            .expiresIn(accessToken.lifetime)
            .scopes(accessToken.scope?.let { LinkedHashSet(it.toStringList()) } ?: emptySet())
            .refreshToken(accessTokenResponse.tokens.refreshToken?.value)
            .additionalParameters(LinkedHashMap(accessTokenResponse.getCustomParameters()))
            .build()
    }

    private fun AccessTokenType.tokenType() = when {
        OAuth2AccessToken.TokenType.BEARER.value.equals(value, ignoreCase = true) -> OAuth2AccessToken.TokenType.BEARER
        else -> null
    }


    companion object {

        private const val INVALID_TOKEN_RESPONSE_ERROR_CODE = "invalid_token_response"

        private val STRING_OBJECT_MAP: ParameterizedTypeReference<MutableMap<String, Any?>> =
            object : ParameterizedTypeReference<MutableMap<String, Any?>>() {}
    }
}
