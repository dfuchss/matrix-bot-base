package org.fuchss.matrix.bots.command

import net.folivo.trixnity.client.room.message.text
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import org.fuchss.matrix.bots.IConfig
import org.fuchss.matrix.bots.MatrixBot

class LogoutCommand(private val config: IConfig) : Command() {
    override val name: String = "logout"
    override val help: String = "quits the bot and logs out all sessions"

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
