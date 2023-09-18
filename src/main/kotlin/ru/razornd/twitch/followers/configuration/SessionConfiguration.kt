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

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializer
import org.springframework.security.jackson2.CoreJackson2Module
import org.springframework.security.oauth2.client.jackson2.OAuth2ClientJackson2Module
import org.springframework.security.web.jackson2.WebJackson2Module
import org.springframework.security.web.server.jackson2.WebServerJackson2Module

@Configuration
open class SessionConfiguration {

    @Bean
    @Qualifier("springSessionDefaultRedisSerializer")
    open fun jacksonRedisSessionSerializer(): RedisSerializer<Any> {
        val mapper = ObjectMapper()

        mapper.registerModule(CoreJackson2Module())
        mapper.registerModule(WebJackson2Module())
        mapper.registerModule(WebServerJackson2Module())
        mapper.registerModule(CoreJackson2Module())
        mapper.registerModule(OAuth2ClientJackson2Module())
        mapper.registerModule(JavaTimeModule())

        return Jackson2JsonRedisSerializer(mapper, Any::class.java)
    }

}