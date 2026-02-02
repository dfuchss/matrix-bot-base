package org.fuchss.matrix.bots.helper

import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.EventType
import de.connect2x.trixnity.core.model.events.m.room.PowerLevelsEventContent
import org.fuchss.matrix.bots.MatrixBot

/**
 * Power level required for administrator privileges in a Matrix room.
 */
const val ADMIN_POWER_LEVEL = 100L

/**
 * Power level required for moderator privileges in a Matrix room.
 */
const val MOD_POWER_LEVEL = 50L

/**
 * Minimum power level in a Matrix room (default user level).
 */
const val MIN_POWER_LEVEL = 0L

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
    val levels = getStateEvent<PowerLevelsEventContent>(roomId) ?: return MIN_POWER_LEVEL
    return levels.users[userId ?: this.self()] ?: levels.usersDefault
}

/**
 * Check if a user can invite others to a room.
 * @param[roomId] the room id
 * @param[userId] the id of the user to check (if null, the bot's own id is used)
 * @return true if the user has permission to invite others
 */
suspend fun MatrixBot.canInvite(
    roomId: RoomId,
    userId: UserId? = null
): Boolean {
    val levels = getStateEvent<PowerLevelsEventContent>(roomId) ?: return false
    val levelToInvite = levels.invite
    val userLevel = levels.users[userId ?: this.self()] ?: levels.usersDefault
    return userLevel >= levelToInvite
}

/**
 * Check if a user can send state events in a room.
 * @param[roomId] the room id
 * @param[userId] the id of the user to check (if null, the bot's own id is used)
 * @param[stateEventType] the specific state event type to check (if null, checks general state permission)
 * @return true if the user has permission to send state events
 */
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

/**
 * Check if a user can send messages in a room.
 * @param[roomId] the room id
 * @param[userId] the id of the user to check (if null, the bot's own id is used)
 * @param[eventType] the specific event type to check (if null, checks general message permission)
 * @return true if the user has permission to send messages
 */
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

/**
 * Check if a user has administrator privileges in a room.
 * @param[userId] the user id
 * @param[roomId] the room id
 * @return true if the user has admin power level (100) or higher
 */
suspend fun MatrixBot.isAdminInRoom(
    userId: UserId,
    roomId: RoomId
): Boolean = powerLevel(roomId, userId) >= ADMIN_POWER_LEVEL

/**
 * Check if a user has moderator privileges in a room.
 * @param[userId] the user id
 * @param[roomId] the room id
 * @return true if the user has moderator power level (50) or higher
 */
suspend fun MatrixBot.isModerator(
    userId: UserId,
    roomId: RoomId
): Boolean = powerLevel(roomId, userId) >= MOD_POWER_LEVEL
