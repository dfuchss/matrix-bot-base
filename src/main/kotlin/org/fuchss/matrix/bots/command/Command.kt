package org.fuchss.matrix.bots.command

import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent
import org.fuchss.matrix.bots.MatrixBot
import org.fuchss.matrix.bots.emoji
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Abstract base class for bot commands.
 *
 * Commands are invoked by users through messages in Matrix rooms that match the command pattern.
 * Each command must implement the [execute] method to define its behavior.
 */
abstract class Command {
    protected val logger: Logger = LoggerFactory.getLogger(this::class.java)

    /**
     * The name of the command that users will use to invoke it.
     */
    abstract val name: String

    /**
     * Parameter description shown in help text. Empty string if command takes no parameters.
     */
    open val params: String = ""

    /**
     * Help text describing what the command does.
     */
    abstract val help: String

    /**
     * Whether to automatically acknowledge command execution with a reaction emoji.
     */
    open val autoAcknowledge: Boolean = false

    companion object {
        /**
         * The emoji used to acknowledge command execution.
         */
        @JvmStatic
        val ACK_EMOJI = ":heavy_check_mark:".emoji()
    }

    /**
     * Execute the command.
     * @param[matrixBot] The bot to execute the command.
     * @param[sender] The sender of the command.
     * @param[roomId] The room to execute the command in.
     * @param[parameters] The parameters of the command.
     * @param[textEventId] The text event id of the command.
     * @param[textEvent] The text event of the command.
     */
    abstract suspend fun execute(
        matrixBot: MatrixBot,
        sender: UserId,
        roomId: RoomId,
        parameters: String,
        textEventId: EventId,
        textEvent: RoomMessageEventContent.TextBased.Text
    )
}
