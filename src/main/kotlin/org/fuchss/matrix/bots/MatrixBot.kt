package org.fuchss.matrix.bots

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import net.folivo.trixnity.client.MatrixClient
import net.folivo.trixnity.client.room
import net.folivo.trixnity.client.room.RoomService
import net.folivo.trixnity.client.store.TimelineEvent
import net.folivo.trixnity.clientserverapi.client.RoomApiClient
import net.folivo.trixnity.clientserverapi.client.SyncApiClient
import net.folivo.trixnity.clientserverapi.client.SyncState
import net.folivo.trixnity.core.ClientEventEmitter
import net.folivo.trixnity.core.Subscriber
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.ClientEvent
import net.folivo.trixnity.core.model.events.EventContent
import net.folivo.trixnity.core.model.events.StateEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.core.model.events.originTimestampOrNull
import net.folivo.trixnity.core.model.events.roomIdOrNull
import net.folivo.trixnity.core.model.events.senderOrNull
import net.folivo.trixnity.core.serialization.events.contentType
import net.folivo.trixnity.core.subscribeContent
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
        matrixClient.api.sync.subscribeContent { event -> handleJoinEvent(event) }
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
     * Access to the [RoomApiClient] to create rooms and manage users.
     */
    fun roomApi() = matrixClient.api.room

    /**
     * Get teh content mappings for [getStateEvent]
     */
    fun contentMappings() = matrixClient.api.room.contentMappings

    /**
     * Get the state event of a certain type
     * @param[type] the type
     * @param[roomId] the roomId
     * @return the state event content
     */
    suspend fun getStateEvent(
        type: String,
        roomId: RoomId
    ): Result<StateEventContent> = matrixClient.api.room.getStateEvent(type, roomId)

    /**
     * Send a certain state event
     * @param[roomId] the id of the room
     * @param[eventContent] the content of the state event
     */
    suspend fun sendStateEvent(
        roomId: RoomId,
        eventContent: StateEventContent
    ): Result<EventId> = matrixClient.api.room.sendStateEvent(roomId, eventContent)

    /**
     * Get a state event from a room
     * @param[C] the type of the event [StateEventContent]
     * @param[roomId] the room to get the event from
     * @return the event
     */
    suspend inline fun <reified C : StateEventContent> getStateEvent(roomId: RoomId): Result<C> {
        val type = contentMappings().state.contentType(C::class)
        @Suppress("UNCHECKED_CAST")
        return getStateEvent(type, roomId) as Result<C>
    }

    /**
     * Get a timeline event from a room and event id.
     * @param[roomId] the room id
     * @param[eventId] the event id
     * @return the timeline event or null if not found.
     */
    suspend fun getTimelineEvent(
        roomId: RoomId,
        eventId: EventId
    ): TimelineEvent? {
        val timelineEvent = room().getTimelineEvent(roomId, eventId).firstWithTimeout { it?.content != null }
        if (timelineEvent == null) {
            logger.error("Cannot get timeline event for $eventId within the given time ..")
            return null
        }
        return timelineEvent
    }

    /**
     * Get the bot's user id
     * @return the user id of the bot
     */
    fun self() = matrixClient.userId

    /**
     * Subscribe to a certain class of event. Note that you can only subscribe for events that are sent by a [users][IConfig.isUser] by default.
     *
     * @param[clazz] the class of event to subscribe
     * @param[subscriber] the function to invoke for the events
     * @param[listenNonUsers] whether you want to subscribe for events from non-users
     * @param[listenBotEvents] whether you want to subscribe for events from the bot itself
     * @see [SyncApiClient.subscribe]
     */
    fun <T : EventContent> subscribeContent(
        clazz: KClass<T>,
        subscriber: Subscriber<ClientEvent<T>>,
        listenNonUsers: Boolean = false,
        listenBotEvents: Boolean = false
    ) {
        matrixClient.api.sync.subscribeContent(clazz, ClientEventEmitter.Priority.DEFAULT) { event ->
            if (isValidEventFromUser(
                    event,
                    listenNonUsers,
                    listenBotEvents
                )
            ) {
                subscriber(event)
            }
        }
    }

    /**
     * Subscribe to a certain class of event. Note that you can only subscribe for events that are sent by an admin by default.
     * @param[subscriber] the function to invoke for the events
     * @param[listenNonUsers] whether you want to subscribe for events from non-users
     * @param[listenBotEvents] whether you want to subscribe for events from the bot itself
     * @see MatrixBot.subscribeContent
     */
    inline fun <reified T : EventContent> subscribeContent(
        listenNonUsers: Boolean = false,
        listenBotEvents: Boolean = false,
        noinline subscriber: Subscriber<ClientEvent<T>>
    ) {
        subscribeContent(T::class, subscriber, listenNonUsers, listenBotEvents)
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
        matrixClient.api.user.setDisplayName(matrixClient.userId, newName)
    }

    /**
     * Rename the bot in a certain room.
     * @param[roomId] the room id of the room
     * @param[newNameInRoom] the bot's new name in the room
     */
    suspend fun renameInRoom(
        roomId: RoomId,
        newNameInRoom: String
    ) {
        val members = matrixClient.api.room.getMembers(roomId).getOrNull() ?: return
        val myself = members.firstOrNull { it.stateKey == matrixClient.userId.full }?.content ?: return
        val newState = myself.copy(displayName = newNameInRoom)
        matrixClient.api.room.sendStateEvent(roomId, newState, stateKey = matrixClient.userId.full, asUserId = matrixClient.userId)
    }

    private fun isValidEventFromUser(
        event: ClientEvent<*>,
        listenNonUsers: Boolean,
        listenBotEvents: Boolean
    ): Boolean {
        if (!config.isUser(event.senderOrNull) && !listenNonUsers) return false
        if (event.senderOrNull == matrixClient.userId && !listenBotEvents) return false
        val timeOfOrigin = event.originTimestampOrNull
        return !(timeOfOrigin == null || Instant.fromEpochMilliseconds(timeOfOrigin) < runningTimestamp)
    }

    private suspend fun handleJoinEvent(event: ClientEvent<MemberEventContent>) {
        val roomId = event.roomIdOrNull ?: return

        if (!config.isUser(event.senderOrNull) || event.senderOrNull == self()) return

        if (event.content.membership != Membership.JOIN) {
            logger.debug("Got Membership Event: {}", event)
            return
        }

        val room = matrixClient.room.getById(roomId).firstWithTimeout { it != null } ?: return
        if (room.membership != Membership.INVITE) return

        logger.info("Joining Room: $roomId by invitation of ${event.senderOrNull?.full ?: "Unknown User"}")
        matrixClient.api.room.joinRoom(roomId)
    }

    private fun registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(
            object : Thread() {
                override fun run() {
                    runBlocking { if (running) quit() }
                }
            }
        )
    }
}
