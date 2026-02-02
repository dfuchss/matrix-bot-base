package org.fuchss.matrix.bots.command

import de.connect2x.trixnity.client.room.message.text
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent
import org.fuchss.matrix.bots.MatrixBot
import org.fuchss.matrix.bots.helper.isBotAdmin

/**
 * Command to quit the bot without logging out.
 *
 * This command is restricted to bot admins only. When executed, it stops the bot
 * but preserves the active sessions, allowing the bot to resume later without re-authentication.
 */
class QuitCommand : Command() {
    override val name: String = "quit"
    override val help: String = "quits the bot without logging out"
    override val autoAcknowledge = true

    /**
     * Quit the bot.
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
        if (!matrixBot.isBotAdmin(sender)) {
            matrixBot.room().sendMessage(roomId) { text("You are not an admin.") }
            return
        }
        matrixBot.quit()
    }
}
