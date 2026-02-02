package org.fuchss.matrix.bots.helper

import de.connect2x.trixnity.client.room.message.MessageBuilder
import de.connect2x.trixnity.client.room.message.text
import de.connect2x.trixnity.core.model.RoomAliasId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.m.room.CanonicalAliasEventContent
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import org.fuchss.matrix.bots.MatrixBot

private const val MATRIX_TO_PREFIX = "https://matrix.to/#/"

/**
 * Format a markdown message and send it using a [MessageBuilder]
 * @param[markdown] the plain Markdown text
 */
fun MessageBuilder.markdown(markdown: String) {
    val document = Parser.builder().build().parse(markdown)
    val html = HtmlRenderer.builder().build().render(document)
    text(markdown, format = "org.matrix.custom.html", formattedBody = html)
}

/**
 * Create a matrix.to link for a RoomId
 * @return the matrix.to link
 */
fun RoomId.matrixTo(): String = "$MATRIX_TO_PREFIX${this.full}?via=${this.full.substringAfter(":")}"

/**
 * Create a matrix.to link for a UserId
 * @return the matrix.to link
 */
fun UserId.matrixTo(): String = "${MATRIX_TO_PREFIX}${this.full}"

/**
 * Indicates if a string is a valid RoomId (syntax)
 */
fun String.syntaxOfRoomId(): Boolean {
    var cleanedInput = this.trim()
    if (cleanedInput.startsWith(MATRIX_TO_PREFIX)) {
        cleanedInput = cleanedInput.removePrefix(MATRIX_TO_PREFIX)
        cleanedInput = cleanedInput.substringBefore("?")
    }
    return cleanedInput.matches(Regex("^![a-zA-Z0-9]+:[a-zA-Z0-9.]+\$")) || cleanedInput.matches(Regex("^#[a-zA-Z0-9_-]+:[a-zA-Z0-9._-]+\$"))
}

/**
 * Extract a RoomId from a string. The string can be a matrix.to link or a room id.
 * @return the RoomId or null if the string is not a valid room id
 */
suspend fun String.toInternalRoomIdOrNull(matrixBot: MatrixBot): RoomId? {
    var cleanedInput = this.trim()
    if (cleanedInput.startsWith(MATRIX_TO_PREFIX)) {
        cleanedInput = cleanedInput.removePrefix(MATRIX_TO_PREFIX)
        cleanedInput = cleanedInput.substringBefore("?")
    }

    if (cleanedInput.startsWith("#")) {
        // Alias RoomId
        return matrixBot.resolvePublicRoomIdOrNull(cleanedInput)
    }

    if (cleanedInput.matches(Regex("^![a-zA-Z0-9]+:[a-zA-Z0-9.]+\$"))) {
        return RoomId(cleanedInput)
    }
    return null
}

/**
 * Resolve a public room alias to its internal room ID.
 *
 * Searches through all joined rooms to find one with a matching canonical alias or alternative alias.
 *
 * @param publicRoomAlias The room alias to resolve (e.g., "#room:server.com")
 * @return The resolved room ID, or null if no matching room is found
 */
suspend fun MatrixBot.resolvePublicRoomIdOrNull(publicRoomAlias: String): RoomId? {
    val roomAlias = RoomAliasId(publicRoomAlias)

    val allKnownRooms = roomApi().getJoinedRooms().getOrThrow()
    for (room in allKnownRooms) {
        val aliasState = getStateEvent<CanonicalAliasEventContent>(room) ?: continue
        if (aliasState.alias == roomAlias) {
            return room
        }
        if (roomAlias in (aliasState.aliases ?: emptySet())) {
            return room
        }
    }
    return null
}
