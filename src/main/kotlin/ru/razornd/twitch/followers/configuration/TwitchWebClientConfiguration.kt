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

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@Qualifier
@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FUNCTION)
annotation class TwitchClient


@ConfigurationProperties("twitch.client")
internal data class TwitchWebClientProperties(val baseUrl: String = "https://api.twitch.tv/helix/")

@Configuration
@EnableConfigurationProperties(TwitchWebClientProperties::class)
open class TwitchWebClientConfiguration internal constructor(private val properties: TwitchWebClientProperties) {

    @Bean
    @TwitchClient
    open fun twitchWebClient(builder: WebClient.Builder): WebClient {
        return builder.baseUrl(properties.baseUrl).build()
    }
}