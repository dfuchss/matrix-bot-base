package org.fuchss.matrix.bots.command

import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent
import org.fuchss.matrix.bots.IConfig
import org.fuchss.matrix.bots.MatrixBot
import org.fuchss.matrix.bots.helper.markdown

/**
 * Command that displays available bot commands and their usage.
 *
 * Lists all registered commands with their parameters and help text in a formatted message.
 *
 * @param config The bot configuration containing the command prefix
 * @param botName The display name of the bot
 * @param commandGetter Function that returns the list of available commands
 */
class HelpCommand(
    private val config: IConfig,
    private val botName: String,
    private val commandGetter: () -> List<Command>
) : Command() {
    override val name: String = "help"
    override val help: String = "shows this help message"

    /**
     * Show the help message.
     * @param[matrixBot] The bot to show the help message.
     * @param[sender] The sender of the command.
     * @param[roomId] The room to show the help message in.
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
        var helpMessage = "This is $botName. You can use the following commands:\n"

        for (command in commandGetter()) {
            helpMessage += "\n* `!${config.prefix} ${command.name} ${command.params} - ${command.help}`"
        }

        matrixBot.room().sendMessage(roomId) { markdown(helpMessage) }
    }
}
