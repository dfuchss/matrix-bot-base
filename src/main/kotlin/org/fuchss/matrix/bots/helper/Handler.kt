package org.fuchss.matrix.bots.helper

import net.folivo.trixnity.client.MatrixClient
import net.folivo.trixnity.client.media.okio.OkioMediaStore
import net.folivo.trixnity.client.room
import net.folivo.trixnity.client.store.repository.exposed.createExposedRepositoriesModule
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent
import net.folivo.trixnity.core.model.events.idOrNull
import net.folivo.trixnity.core.model.events.m.room.EncryptedMessageEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.TextMessageEventContent
import net.folivo.trixnity.core.model.events.roomIdOrNull
import net.folivo.trixnity.core.model.events.senderOrNull
import okio.Path.Companion.toOkioPath
import org.fuchss.matrix.bots.IConfig
import org.fuchss.matrix.bots.MatrixBot
import org.fuchss.matrix.bots.command.Command
import org.fuchss.matrix.bots.firstWithTimeout
import org.jetbrains.exposed.sql.Database
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File

private val logger: Logger = LoggerFactory.getLogger(MatrixBot::class.java)

suspend fun createRepositoriesModule(config: IConfig) =
    createExposedRepositoriesModule(database = Database.connect("jdbc:h2:${config.dataDirectory}/database;DB_CLOSE_DELAY=-1"))

fun createMediaStore(config: IConfig) = OkioMediaStore(File(config.dataDirectory + "/media").toOkioPath())

suspend fun handleEncryptedTextMessage(
    commands: List<Command>,
    event: ClientEvent<EncryptedMessageEventContent>,
    matrixClient: MatrixClient,
    matrixBot: MatrixBot,
    config: IConfig
) {
    val roomId = event.roomIdOrNull ?: return
    val eventId = event.idOrNull ?: return

    logger.debug("Waiting for decryption of {} ..", event)
    val decryptedEvent = matrixClient.room.getTimelineEvent(roomId, eventId).firstWithTimeout { it?.content != null }
    if (decryptedEvent != null) {
        logger.debug("Decryption of {} was successful", event)
    }

    if (decryptedEvent == null) {
        logger.error("Cannot decrypt event $event within the given time ..")
        return
    }

    val content = decryptedEvent.content?.getOrNull() ?: return
    if (content is TextMessageEventContent) {
        handleTextMessage(commands, roomId, event.senderOrNull, content, matrixBot, config)
    }
}

suspend fun handleTextMessage(
    commands: List<Command>,
    roomId: RoomId?,
    sender: UserId?,
    content: TextMessageEventContent,
    matrixBot: MatrixBot,
    config: IConfig
) {
    if (roomId == null || sender == null) {
        return
    }

    var message = content.body
    if (!message.startsWith("!${config.prefix}")) {
        return
    }

    message = message.substring("!${config.prefix}".length).trim()

    val command = message.split(Regex(" "), 2)[0]
    val parameters = message.substring(command.length).trim()

    commands.find { it.name == command }?.execute(matrixBot, sender, roomId, parameters)
}
