package org.fuchss.matrix.bots

import de.connect2x.trixnity.client.MatrixClient
import de.connect2x.trixnity.client.room
import de.connect2x.trixnity.client.room.RoomService
import de.connect2x.trixnity.client.store.TimelineEvent
import de.connect2x.trixnity.clientserverapi.client.RoomApiClient
import de.connect2x.trixnity.clientserverapi.client.SyncApiClient
import de.connect2x.trixnity.clientserverapi.client.SyncState
import de.connect2x.trixnity.clientserverapi.model.user.ProfileField
import de.connect2x.trixnity.core.ClientEventEmitter
import de.connect2x.trixnity.core.Subscriber
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ClientEvent
import de.connect2x.trixnity.core.model.events.EventContent
import de.connect2x.trixnity.core.model.events.StateEventContent
import de.connect2x.trixnity.core.model.events.idOrNull
import de.connect2x.trixnity.core.model.events.m.room.MemberEventContent
import de.connect2x.trixnity.core.model.events.m.room.Membership
import de.connect2x.trixnity.core.model.events.originTimestampOrNull
import de.connect2x.trixnity.core.model.events.roomIdOrNull
import de.connect2x.trixnity.core.model.events.senderOrNull
import de.connect2x.trixnity.core.model.events.stateKeyOrNull
import de.connect2x.trixnity.core.serialization.events.contentType
import de.connect2x.trixnity.core.subscribeContent
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * This class provides encapsulates a [MatrixClient] and its [IConfig] to provide a high level bot interface.
 */
class MatrixBot(
    private val matrixClient: MatrixClient,
    private val config: IConfig
) {
    private val logger = LoggerFactory.getLogger(MatrixBot::class.java)

    @OptIn(ExperimentalTime::class)
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

        matrixClient.stopSync()
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
     * Get the content mappings for [getStateEvent]
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
    ): Result<StateEventContent> = matrixClient.api.room.getStateEventContent(type, roomId)

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
    suspend inline fun <reified C : StateEventContent> getStateEvent(roomId: RoomId): C? {
        val logger = LoggerFactory.getLogger(MatrixBot::class.java)
        val type = contentMappings().state.contentType(C::class)
        val stateResult = getStateEvent(type, roomId).onFailure { logger.error(it.message, it) }

        val data = stateResult.getOrNull() ?: return null
        if (data is C) {
            return data
        }
        throw IllegalStateException("Expected type: ${C::class.java} but got ${data::class.java}")
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
     * Subscribe to a certain class of event. Note that you can only subscribe for events that are sent by an admin by default.
     * @param[subscriber] the function to invoke for the events
     * @param[listenNonUsers] whether you want to subscribe for events from non-users
     * @param[listenBotEvents] whether you want to subscribe for events from the bot itself
     */
    inline fun <reified T : EventContent> subscribeContent(
        listenNonUsers: Boolean = false,
        listenBotEvents: Boolean = false,
        noinline subscriber: suspend (EventId, UserId, RoomId, T) -> Unit
    ) {
        subscribeContent(T::class, { event ->
            val eventId = event.idOrNull
            val sender = event.senderOrNull
            val roomId = event.roomIdOrNull
            if (eventId != null && sender != null && roomId != null) {
                subscriber(eventId, sender, roomId, event.content)
            }
        }, listenNonUsers, listenBotEvents)
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
        matrixClient.api.user
            .setProfileField(matrixClient.userId, ProfileField.DisplayName(newName))
            .getOrThrow()
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
        val members =
            matrixClient.api.room
                .getMembers(roomId)
                .getOrNull() ?: return
        val myself = members.firstOrNull { it.stateKey == matrixClient.userId.full }?.content ?: return
        val newState = myself.copy(displayName = newNameInRoom)
        matrixClient.api.room
            .sendStateEvent(roomId, newState, stateKey = matrixClient.userId.full)
            .getOrThrow()
    }

    @OptIn(ExperimentalTime::class)
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
        val stateKey = event.stateKeyOrNull ?: return

        if (stateKey != self().full) return

        if (!config.isUser(event.senderOrNull) || event.senderOrNull == self()) return

        if (event.content.membership != Membership.INVITE) {
            return
        }

        // Check if already joined ..
        val room = matrixClient.room.getById(roomId).firstWithTimeout { it != null } ?: return
        if (room.membership != Membership.INVITE) return

        logger.info("Joining Room: $roomId by invitation of ${event.senderOrNull?.full ?: "Unknown User"}")
        matrixClient.api.room
            .joinRoom(roomId)
            .onFailure { logger.error("Could not join room $roomId: ${it.message}", it) }
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
