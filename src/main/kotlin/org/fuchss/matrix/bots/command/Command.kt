package org.fuchss.matrix.bots.command

import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import org.fuchss.matrix.bots.MatrixBot
import org.slf4j.Logger
import org.slf4j.LoggerFactory

abstract class Command {
    protected val logger: Logger = LoggerFactory.getLogger(this::class.java)

    abstract val name: String
    open val params: String = ""
    abstract val help: String

    /**
     * Execute the command.
     * @param[matrixBot] The bot to execute the command.
     * @param[sender] The sender of the command.
     * @param[roomId] The room to execute the command in.
     * @param[parameters] The parameters of the command.
     */
    abstract suspend fun execute(
        matrixBot: MatrixBot,
        sender: UserId,
        roomId: RoomId,
        parameters: String
    )
}
