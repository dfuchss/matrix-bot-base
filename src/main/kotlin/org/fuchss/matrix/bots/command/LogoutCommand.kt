package org.fuchss.matrix.bots.command

import de.connect2x.trixnity.client.room.message.text
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent
import org.fuchss.matrix.bots.IConfig
import org.fuchss.matrix.bots.MatrixBot

/**
 * Command to quit the bot and log out all sessions.
 *
 * This command is restricted to bot admins only. When executed, it stops the bot
 * and logs out all active sessions of the bot user.
 *
 * @param config The bot configuration for admin verification
 */
class LogoutCommand(
    private val config: IConfig
) : Command() {
    override val name: String = "logout"
    override val help: String = "quits the bot and logs out all sessions"
    override val autoAcknowledge = true

    /**
     * Quit the bot and logout all sessions.
     * @param[matrixBot] The bot to quit.
     * @param[sender] The sender of the command.
     * @param[roomId] The room to execute the command in.
     * @param[parameters] The parameters of the command.
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
        if (!config.isBotAdmin(sender)) {
            matrixBot.room().sendMessage(roomId) { text("You are not an admin.") }
            return
        }

        matrixBot.quit(logout = true)
    }
}
