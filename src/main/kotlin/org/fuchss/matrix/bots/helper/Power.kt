package org.fuchss.matrix.bots.helper

import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.EventType
import de.connect2x.trixnity.core.model.events.m.room.PowerLevelsEventContent
import org.fuchss.matrix.bots.MatrixBot

const val ADMIN_POWER_LEVEL = 100L
const val MOD_POWER_LEVEL = 50L

/**
 * Get the current permission level of the bot in a room
 * @param[roomId] the id to the room
 * @param[userId] the id of the user to get the permission level of (if null, the bot's own id is used)
 * @return the permission level of the bot
 */
suspend fun MatrixBot.powerLevel(
    roomId: RoomId,
    userId: UserId? = null
): Long {
    val levels = getStateEvent<PowerLevelsEventContent>(roomId) ?: return 0
    return levels.users[userId ?: this.self()] ?: levels.usersDefault
}

suspend fun MatrixBot.canInvite(
    roomId: RoomId,
    userId: UserId? = null
): Boolean {
    val levels = getStateEvent<PowerLevelsEventContent>(roomId) ?: return false
    val levelToInvite = levels.invite
    val userLevel = levels.users[userId ?: this.self()] ?: levels.usersDefault
    return userLevel >= levelToInvite
}

suspend fun MatrixBot.canSendStateEvents(
    roomId: RoomId,
    userId: UserId? = null,
    stateEventType: EventType? = null
): Boolean {
    val levels = getStateEvent<PowerLevelsEventContent>(roomId) ?: return false
    val levelToSendState = levels.events[stateEventType] ?: levels.stateDefault
    val userLevel = levels.users[userId ?: this.self()] ?: levels.usersDefault
    return userLevel >= levelToSendState
}

suspend fun MatrixBot.canSendMessages(
    roomId: RoomId,
    userId: UserId? = null,
    eventType: EventType? = null
): Boolean {
    val levels = getStateEvent<PowerLevelsEventContent>(roomId) ?: return false
    val levelToSendMessages = levels.events[eventType] ?: levels.eventsDefault
    val userLevel = levels.users[userId ?: this.self()] ?: levels.usersDefault
    return userLevel >= levelToSendMessages
}

suspend fun UserId.isAdminInRoom(
    matrixBot: MatrixBot,
    roomId: RoomId
): Boolean = matrixBot.powerLevel(roomId, this) >= ADMIN_POWER_LEVEL

suspend fun UserId.isModerator(
    matrixBot: MatrixBot,
    roomId: RoomId
): Boolean = matrixBot.powerLevel(roomId, this) >= MOD_POWER_LEVEL
