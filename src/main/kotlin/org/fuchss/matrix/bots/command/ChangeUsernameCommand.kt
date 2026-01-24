package org.fuchss.matrix.bots.command

import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent
import org.fuchss.matrix.bots.MatrixBot
import org.fuchss.matrix.bots.helper.canSendMessages
import org.fuchss.matrix.bots.helper.isModerator
import org.jetbrains.exposed.sql.Table.Dual.text

class ChangeUsernameCommand(
    private val globally: Boolean = false
) : Command() {
    override val name: String = "name"
    override val params: String = "{NEW_NAME}"
    override val help: String = "sets the display name of the bot for this channel to NEW_NAME"

    /**
     * Change the username of the bot.
     * @param[matrixBot] The bot to change the username of.
     * @param[sender] The sender of the command.
     * @param[roomId] The room to execute the command in.
     * @param[parameters] the new name of the bot.
     * @param[textEventId] The event of the command.
     * @param[textEvent] The event of the command.
     */
    override suspend fun execute(
        matrixBot: MatrixBot,
        sender: UserId,
        roomId: RoomId,
        parameters: String,
        textEventId: EventId,
        textEvent: RoomMessageEventContent.TextBased.Text
    ) {
        if (!(matrixBot.canSendMessages(roomId))) {
            logger.error("I'm not even allowed to send messages in $roomId, skipping rename")
            return
        }

        if (parameters.isBlank()) {
            matrixBot.room().sendMessage(roomId) { text("Please provide a new name for the bot.") }
            return
        }

        if (globally) {
            matrixBot.rename(parameters)
            return
        }

        if (!sender.isModerator(matrixBot, roomId)) {
            matrixBot.room().sendMessage(roomId) { text("You are not a moderator in this room.") }
            return
        }

        if (parameters.isNotBlank()) {
            matrixBot.renameInRoom(roomId, parameters)
        }
    }
}
