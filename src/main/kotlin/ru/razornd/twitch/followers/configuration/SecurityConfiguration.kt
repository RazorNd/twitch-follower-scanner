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

package ru.razornd.twitch.followers.configuration

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.config.web.server.invoke
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest
import org.springframework.security.oauth2.client.endpoint.ReactiveOAuth2AccessTokenResponseClient
import org.springframework.security.oauth2.client.endpoint.WebClientReactiveAuthorizationCodeTokenResponseClient
import org.springframework.security.oauth2.client.oidc.userinfo.OidcReactiveOAuth2UserService
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository
import org.springframework.security.oauth2.client.web.server.DefaultServerOAuth2AuthorizationRequestResolver
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.security.web.server.authentication.HttpStatusServerEntryPoint
import org.springframework.security.web.server.csrf.CookieServerCsrfTokenRepository
import reactor.core.publisher.Mono
import ru.razornd.twitch.followers.security.support.OAuth2AccessTokenResponseBodyExtractor

@Configuration
open class SecurityConfiguration {

    @Bean
    open fun securityWebFilterChain(
        http: ServerHttpSecurity,
        registrationRepository: ReactiveClientRegistrationRepository
    ): SecurityWebFilterChain {
        return http {
            authorizeExchange {
                authorize("/actuator/**", permitAll)
                authorize(anyExchange, authenticated)
            }
            csrf { csrfTokenRepository = CookieServerCsrfTokenRepository() }
            oauth2Login {
                authorizationRequestResolver =
                    DefaultServerOAuth2AuthorizationRequestResolver(registrationRepository).apply {
                        setAuthorizationRequestCustomizer {
                            it.additionalParameters { params ->
                                params["claims"] = """{"id_token": {"picture": null, "preferred_username": null}}"""
                            }
                        }
                    }

            }
            exceptionHandling { authenticationEntryPoint = HttpStatusServerEntryPoint(HttpStatus.UNAUTHORIZED) }
        }
    }

    @Bean
    open fun accessTokenResponseClient(): ReactiveOAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> {
        return WebClientReactiveAuthorizationCodeTokenResponseClient().apply {
            setBodyExtractor(OAuth2AccessTokenResponseBodyExtractor())
        }
    }

    @Bean
    open fun oidcReactiveOAuth2UserService(): OidcReactiveOAuth2UserService {
        return OidcReactiveOAuth2UserService().apply {
            setOauth2UserService { Mono.empty() }
        }
    }
}