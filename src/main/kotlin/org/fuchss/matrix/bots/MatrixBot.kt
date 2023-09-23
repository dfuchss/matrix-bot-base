package org.fuchss.matrix.bots

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import net.folivo.trixnity.client.MatrixClient
import net.folivo.trixnity.client.room
import net.folivo.trixnity.client.room.RoomService
import net.folivo.trixnity.clientserverapi.client.RoomsApiClient
import net.folivo.trixnity.clientserverapi.client.SyncApiClient
import net.folivo.trixnity.clientserverapi.client.SyncState
import net.folivo.trixnity.core.EventSubscriber
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.EventContent
import net.folivo.trixnity.core.model.events.StateEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.core.model.events.originTimestampOrNull
import net.folivo.trixnity.core.model.events.roomIdOrNull
import net.folivo.trixnity.core.model.events.senderOrNull
import net.folivo.trixnity.core.serialization.events.fromClass
import net.folivo.trixnity.core.subscribe
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass

/**
 * This class provides encapsulates a [MatrixClient] and its [IConfig] to provide a high level bot interface.
 */
class MatrixBot(private val matrixClient: MatrixClient, private val config: IConfig) {

    private val logger = LoggerFactory.getLogger(MatrixBot::class.java)

    private val runningTimestamp = Clock.System.now()
    private val validStates = listOf(SyncState.RUNNING, SyncState.INITIAL_SYNC, SyncState.STARTED)
    private val runningLock = Semaphore(1, 1)
    private var running: Boolean = false

    private var logout: Boolean = false

    init {
        matrixClient.api.sync.subscribe { event -> handleJoinEvent(event) }
    }

    /**
     * Starts the bot. Note that this method blocks until [quit] will be executed from another thread.
     * @return true if the bot was logged out, false if the bot simply quit.
     */
    suspend fun startBlocking(): Boolean {
        running = true
        registerShutdownHook()

        logger.info("Starting Sync!")
        matrixClient.startSync()
        delay(1000)

        logger.info("Waiting for events ..")
        runningLock.acquire()

        logger.info("Shutting down!")
        while (matrixClient.syncState.value in validStates) {
            delay(500)
        }
        running = false
        if (logout) {
            matrixClient.api.authentication.logoutAll()
        }

        matrixClient.stop()

        return logout
    }

    /**
     * Access the [RoomService] to send messages.
     */
    fun room() = matrixClient.room

    /**
     * Get teh content mappings for [getStateEvent]
     */
    fun contentMappings() = matrixClient.api.rooms.contentMappings

    /**
     * Get the state event of a certain type
     * @param[type] the type
     * @param[roomId] the roomId
     * @return the state event content
     */
    suspend fun getStateEvent(type: String, roomId: RoomId): Result<StateEventContent> = matrixClient.api.rooms.getStateEvent(type, roomId)

    /**
     * Send a certain state event
     * @param[roomId] the id of the room
     * @param[eventContent] the content of the state event
     */
    suspend fun sendStateEvent(
        roomId: RoomId,
        eventContent: StateEventContent
    ): Result<EventId> = matrixClient.api.rooms.sendStateEvent(roomId, eventContent)

    /**
     * Get a state event from a room
     * @param[C] the type of the event [StateEventContent]
     * @param[roomId] the room to get the event from
     * @return the event
     */
    suspend inline fun <reified C : StateEventContent> getStateEvent(
        roomId: RoomId
    ): Result<C> {
        val type = contentMappings().state.fromClass(C::class).type
        @Suppress("UNCHECKED_CAST")
        return getStateEvent(type, roomId) as Result<C>
    }

    /**
     * Access to the [RoomsApiClient] to create rooms and manage users.
     */
    fun rooms() = matrixClient.api.rooms

    /**
     * Get the bot's user id
     * @return the user id of the bot
     */
    fun self() = matrixClient.userId

    /**
     * Subscribe to a certain class of event. Note that you can only subscribe for events that are sent by a [users][IConfig.isUser] by default.
     *
     * @param[clazz] the clas of event to subscribe
     * @param[subscriber] the function to invoke for the events
     * @param[listenNonUsers] whether you want to subscribe for events from non-users
     * @see [SyncApiClient.subscribe]
     */
    fun <T : EventContent> subscribe(clazz: KClass<T>, subscriber: EventSubscriber<T>, listenNonUsers: Boolean = false) {
        matrixClient.api.sync.subscribe(clazz) { event -> if (isValidEventFromUser(event, listenNonUsers)) subscriber(event) }
    }

    /**
     * Subscribe to a certain class of event. Note that you can only subscribe for events that are sent by an admin by default.
     * @param[subscriber] the function to invoke for the events
     * @param[listenNonUsers] whether you want to subscribe for events from non-users
     * @see MatrixBot.subscribe
     */
    inline fun <reified T : EventContent> subscribe(listenNonUsers: Boolean = false, noinline subscriber: EventSubscriber<T>) {
        subscribe(T::class, subscriber, listenNonUsers)
    }

    /**
     * Quit the bot. This will end the lock of [startBlocking]. Additionally, it will log out all instances of the bot user.
     */
    suspend fun quit(logout: Boolean = false) {
        this.logout = logout
        matrixClient.stopSync()
        runningLock.release()
    }

    suspend fun rename(newName: String) {
        matrixClient.api.users.setDisplayName(matrixClient.userId, newName)
    }

    /**
     * Rename the bot in a certain room.
     * @param[roomId] the room id of the room
     * @param[newNameInRoom] the bot's new name in the room
     */
    suspend fun renameInRoom(roomId: RoomId, newNameInRoom: String) {
        val members = matrixClient.api.rooms.getMembers(roomId).getOrNull() ?: return
        val myself = members.firstOrNull { it.stateKey == matrixClient.userId.full }?.content ?: return
        val newState = myself.copy(displayName = newNameInRoom)
        matrixClient.api.rooms.sendStateEvent(roomId, newState, stateKey = matrixClient.userId.full, asUserId = matrixClient.userId)
    }

    private fun isValidEventFromUser(event: Event<*>, listenNonUsers: Boolean): Boolean {
        if (!config.isUser(event.senderOrNull) && !listenNonUsers) return false
        if (event.senderOrNull == matrixClient.userId) return false
        val timeOfOrigin = event.originTimestampOrNull
        return !(timeOfOrigin == null || Instant.fromEpochMilliseconds(timeOfOrigin) < runningTimestamp)
    }

    private suspend fun handleJoinEvent(event: Event<MemberEventContent>) {
        val roomId = event.roomIdOrNull ?: return

        if (!config.isUser(event.senderOrNull) || event.senderOrNull == self()) return

        if (event.content.membership != Membership.JOIN) {
            logger.debug("Got Membership Event: {}", event)
            return
        }

        val room = matrixClient.room.getById(roomId).firstWithTimeout { it != null } ?: return
        if (room.membership != Membership.INVITE) return

        logger.info("Joining Room: $roomId by invitation of ${event.senderOrNull?.full ?: "Unknown User"}")
        matrixClient.api.rooms.joinRoom(roomId)
    }

    private fun registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(object : Thread() {
            override fun run() {
                runBlocking { if (running) quit() }
            }
        })
    }
}