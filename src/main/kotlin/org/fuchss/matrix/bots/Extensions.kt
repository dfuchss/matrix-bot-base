package org.fuchss.matrix.bots

import com.vdurmont.emoji.Emoji
import com.vdurmont.emoji.EmojiManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Same as [Flow.first] but with a defined timeout that leads to null if reached.
 * @param predicate a predicate to filter the results of [Flow.first]
 * @return the result of [Flow.first] or null
 */
suspend fun <T> Flow<T>.firstWithTimeout(
    timeout: Duration = 3000.milliseconds,
    predicate: suspend (T) -> Boolean
): T? {
    val that = this
    return withTimeoutOrNull(timeout) { that.first { predicate(it) } }
}

/**
 * Convert a string emoji to an [Emoji].
 */
fun String.emoji(): String = EmojiManager.getForAlias(this).unicode
