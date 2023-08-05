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

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.security.web.server.csrf.CsrfToken
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/api/xsrf-token")
class XsrfTokenController {

    @GetMapping
    suspend fun getToken(
        @RequestAttribute("org.springframework.security.web.server.csrf.CsrfToken")
        token: Mono<CsrfToken>
    ) = XsrfToken(token.awaitSingle())

    data class XsrfToken(val token: String, val headerName: String) {
        constructor(csrfToken: CsrfToken) : this(csrfToken.token, csrfToken.headerName)
    }
}
