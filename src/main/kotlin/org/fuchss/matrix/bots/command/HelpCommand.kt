package org.fuchss.matrix.bots.command

import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import org.fuchss.matrix.bots.IConfig
import org.fuchss.matrix.bots.MatrixBot
import org.fuchss.matrix.bots.markdown

class HelpCommand(private val config: IConfig, private val botName: String, private val commandGetter: () -> List<Command>) : Command() {
    override val name: String = "help"
    override val help: String = "shows this help message"

    /**
     * Show the help message.
     * @param[matrixBot] The bot to show the help message.
     * @param[sender] The sender of the command.
     * @param[roomId] The room to show the help message in.
     * @param[parameters] The parameters of the command.
     */
    override suspend fun execute(
        matrixBot: MatrixBot,
        sender: UserId,
        roomId: RoomId,
        parameters: String
    ) {
        var helpMessage = "This is $botName. You can use the following commands:\n"

        for (command in commandGetter()) {
            helpMessage += "\n* `!${config.prefix} ${command.name} ${command.params} - ${command.help}`"
        }

        matrixBot.room().sendMessage(roomId) { markdown(helpMessage) }
    }
}
