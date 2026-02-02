package org.fuchss.matrix.bots.helper

import de.connect2x.trixnity.client.room.message.react
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
import org.fuchss.matrix.bots.IConfig
import org.fuchss.matrix.bots.MatrixBot
import org.fuchss.matrix.bots.command.Command
import org.fuchss.matrix.bots.firstWithTimeout
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val logger: Logger = LoggerFactory.getLogger(MatrixBot::class.java)

/**
 * Decrypt an encrypted message event and invoke a handler with the decrypted content.
 *
 * Waits for the message to be decrypted with a timeout, then processes it if it's a text message.
 *
 * @param event The encrypted message event
 * @param matrixBot The bot instance
 * @param handler Function to invoke with the decrypted message details
 */
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

/**
 * Handle an encrypted command by decrypting it first and then executing the appropriate command.
 *
 * @param commands List of available commands
 * @param event The encrypted message event
 * @param matrixBot The bot instance
 * @param config The bot configuration
 * @param defaultCommand Optional default command to use if no matching command is found
 */
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

/**
 * Handle a plain (unencrypted) command event by executing the appropriate command.
 *
 * @param commands List of available commands
 * @param event The room message event
 * @param matrixBot The bot instance
 * @param config The bot configuration
 * @param defaultCommand Optional default command to use if no matching command is found
 */
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

/**
 * Execute a command based on the message content.
 *
 * Parses the message to extract the command name and parameters, then executes
 * the matching command from the provided list. If no matching command is found
 * and a defaultCommand is specified, that command is executed instead.
 *
 * The message must start with "![IConfig.prefix]" to be processed as a command.
 * If [Command.autoAcknowledge] is true, the bot reacts to the message with a checkmark emoji.
 *
 * @param commands List of available commands to match against
 * @param sender The user who sent the command
 * @param matrixBot The bot instance to use for execution
 * @param roomId The room where the command was sent
 * @param textEventId The event ID of the message
 * @param textEvent The text content of the message
 * @param config The bot configuration for prefix validation
 * @param defaultCommand Optional fallback command name if no match is found
 */
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
