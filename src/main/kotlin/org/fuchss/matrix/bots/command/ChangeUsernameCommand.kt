package org.fuchss.matrix.bots.command

import net.folivo.trixnity.client.room.message.text
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import org.fuchss.matrix.bots.MatrixBot
import org.fuchss.matrix.bots.helper.isModerator

class ChangeUsernameCommand : Command() {
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
        if (!sender.isModerator(matrixBot, roomId)) {
            matrixBot.room().sendMessage(roomId) { text("You are not a moderator in this room.") }
            return
        }

        if (parameters.isBlank()) {
            matrixBot.room().sendMessage(roomId) { text("Please provide a new name for the bot.") }
            return
        }

        if (parameters.isNotBlank()) {
            matrixBot.renameInRoom(roomId, parameters)
        }
    }
}
