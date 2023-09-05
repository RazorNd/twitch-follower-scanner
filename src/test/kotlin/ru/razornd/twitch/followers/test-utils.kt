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

package ru.razornd.twitch.followers

import org.assertj.db.type.Changes

fun <T> Changes.captureChanges(block: () -> T): T {

    this.setStartPointNow()
    val response = block()
    this.setEndPointNow()

    return response
}

fun <T> Collection<Changes>.captureChanges(block: () -> T): T {
    for (changes in this) {
        changes.setStartPointNow()
    }

    val response = block()

    for (changes in this.reversed()) {
        changes.setEndPointNow()
    }

    return response
}
