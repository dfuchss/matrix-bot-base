package org.fuchss.matrix.bots.helper

import de.connect2x.trixnity.client.CryptoDriverModule
import de.connect2x.trixnity.client.MediaStoreModule
import de.connect2x.trixnity.client.RepositoriesModule
import de.connect2x.trixnity.client.cryptodriver.libolm.libOlm
import de.connect2x.trixnity.client.media.okio.okio
import de.connect2x.trixnity.client.room.message.react
import de.connect2x.trixnity.client.store.repository.exposed.exposed
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ClientEvent
import de.connect2x.trixnity.core.model.events.idOrNull
import de.connect2x.trixnity.core.model.events.m.room.EncryptedMessageEventContent
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent.TextBased.Text
import de.connect2x.trixnity.core.model.events.roomIdOrNull
import de.connect2x.trixnity.core.model.events.senderOrNull
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

fun createRepositoriesModule(config: IConfig) =
    RepositoriesModule.exposed(database = Database.connect("jdbc:h2:${config.dataDirectory}/database;DB_CLOSE_DELAY=-1"))

fun createMediaStoreModule(config: IConfig) = MediaStoreModule.okio(File(config.dataDirectory + "/media").toOkioPath())

fun createCryptoDriverModule() = CryptoDriverModule.libOlm()

suspend fun decryptMessage(
    event: ClientEvent<EncryptedMessageEventContent>,
    matrixBot: MatrixBot,
    handler: suspend (EventId, UserId, RoomId, Text) -> Unit
) {
    val eventId = event.idOrNull ?: return
    val roomId = event.roomIdOrNull ?: return
    val sender = event.senderOrNull ?: return

    logger.debug("Waiting for decryption of {} ..", event)
    val decryptedEvent = matrixBot.room().getTimelineEvent(roomId, eventId).firstWithTimeout { it?.content != null }
    if (decryptedEvent != null) {
        logger.debug("Decryption of {} was successful", event)
    }

    if (decryptedEvent == null) {
        logger.error("Cannot decrypt event $event within the given time ..")
        return
    }

    val content = decryptedEvent.content?.getOrNull() ?: return
    if (content is Text) {
        handler(eventId, sender, roomId, content)
    }
}

suspend fun handleEncryptedCommand(
    commands: List<Command>,
    event: ClientEvent<EncryptedMessageEventContent>,
    matrixBot: MatrixBot,
    config: IConfig,
    defaultCommand: String? = null
) {
    decryptMessage(event, matrixBot) { eventId, sender, roomId, text ->
        executeCommand(commands, sender, matrixBot, roomId, eventId, text, config, defaultCommand)
    }
}

suspend fun handleCommand(
    commands: List<Command>,
    event: ClientEvent<RoomMessageEventContent>,
    matrixBot: MatrixBot,
    config: IConfig,
    defaultCommand: String? = null
) {
    val roomId = event.roomIdOrNull ?: return
    val sender = event.senderOrNull ?: return
    val eventId = event.idOrNull ?: return
    val content = event.content
    if (content is Text) {
        executeCommand(commands, sender, matrixBot, roomId, eventId, content, config, defaultCommand)
    }
}

private suspend fun executeCommand(
    commands: List<Command>,
    sender: UserId,
    matrixBot: MatrixBot,
    roomId: RoomId,
    textEventId: EventId,
    textEvent: Text,
    config: IConfig,
    defaultCommand: String?
) {
    var message = textEvent.body
    if (!message.startsWith("!${config.prefix}")) {
        return
    }
    message = message.substring("!${config.prefix}".length).trim()

    val command = message.split(Regex(" "), 2)[0]
    var parameters = message.substring(command.length).trim()

    var commandToExecute = commands.find { it.name == command }
    if (commandToExecute == null && defaultCommand != null) {
        commandToExecute = commands.find { it.name == defaultCommand }
        parameters = message
    }

    if (commandToExecute == null) {
        return
    }

    if (commandToExecute.autoAcknowledge) {
        matrixBot.room().sendMessage(roomId) {
            react(textEventId, Command.ACK_EMOJI)
        }
    }

    commandToExecute.execute(matrixBot, sender, roomId, parameters, textEventId, textEvent)
}
