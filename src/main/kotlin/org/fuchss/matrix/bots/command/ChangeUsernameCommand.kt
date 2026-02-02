package org.fuchss.matrix.bots.command

import de.connect2x.trixnity.client.room.message.text
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent
import org.fuchss.matrix.bots.IConfig
import org.fuchss.matrix.bots.MatrixBot
import org.fuchss.matrix.bots.helper.canSendMessages
import org.fuchss.matrix.bots.helper.isModerator

/**
 * Command to change the display name of the bot.
 *
 * When [globally] is `true`, the bot's display name is changed globally.
 * When [globally] is `false`, the bot's display name is changed only for the given room.
 *
 * @param globally controls whether the name change applies globally or only in the current room.
 */
class ChangeUsernameCommand(
    private val config: IConfig,
    private val globally: Boolean = false
) : Command() {
    override val name: String = "name"
    override val params: String = "{NEW_NAME}"
    override val help: String = "sets the display name of the bot to NEW_NAME (for this channel, or globally when configured)"

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

        if (globally && config.isBotAdmin(sender)) {
            matrixBot.rename(parameters)
            return
        } else if (globally) {
            logger.info("User $sender tried to update global bot user name")
        }

        if (!matrixBot.isModerator(sender, roomId)) {
            matrixBot.room().sendMessage(roomId) { text("You are not a moderator in this room.") }
            return
        }

        matrixBot.renameInRoom(roomId, parameters)
    }
}
