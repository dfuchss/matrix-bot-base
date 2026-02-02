package org.fuchss.matrix.bots

import de.connect2x.trixnity.core.model.UserId

/**
 * Configuration interface for Matrix bot instances.
 *
 * This interface defines all required configuration parameters for a Matrix bot,
 * including authentication credentials, server connection details, authorization
 * settings, and storage paths.
 *
 * Implementations must provide:
 * - Matrix server connection details ([baseUrl], [username], [password])
 * - Command prefix for bot interaction ([prefix])
 * - Authorization lists ([admins], [users])
 * - Local storage path ([dataDirectory])
 *
 * The [validate] method should be called after configuration initialization to
 * ensure all required fields are properly set.
 *
 * Example implementation:
 * ```kotlin
 * class BotConfig : IConfig {
 *     override val prefix = "mybot"
 *     override val baseUrl = "https://matrix.example.org"
 *     override val username = "botuser"
 *     override val password = "secret"
 *     override val dataDirectory = "/var/lib/mybot"
 *     override val admins = listOf("@admin:example.org")
 *     override val users = listOf(":example.org")
 * }
 * ```
 */
interface IConfig {
    /**
     * The command prefix the bot listens to. E.g., "bot"
     */
    val prefix: String

    /**
     * The base url of the matrix server. E.g., "https://matrix.invalid.domain"
     */
    val baseUrl: String

    /**
     * The username of the bot's account.
     */
    val username: String

    /**
     * The password of the bot's account.
     */
    val password: String

    /**
     * The path to the databases and media folder
     */
    val dataDirectory: String

    /**
     * The matrix ids of the admins. E.g. "@user:invalid.domain"
     */
    val admins: List<String>

    /**
     * The matrix ids of the authorized users or servers. E.g. "@user:invalid.domain" or ":invalid.domain"
     */
    val users: List<String>

    fun validate() {
        if (prefix.isBlank()) {
            error("Please verify that prefix is not empty!")
        }

        if (baseUrl.isBlank() || username.isBlank() || password.isBlank()) {
            error("Please verify that baseUrl, username, and password are not null!")
        }

        if (dataDirectory.isBlank()) {
            error("Please verify that dataDirectory is not empty!")
        }
        if (admins.isEmpty()) {
            error("No admins specified. This is not allowed. Please specify at least one admin.")
        }
    }

    /**
     * Determine whether a user id belongs to an authorized user.
     * @param[user] the user id to check
     * @return indicator for authorization
     */
    fun isUser(user: UserId?): Boolean {
        if (user == null) {
            return false
        }
        if (users.isEmpty()) {
            return true
        }
        return users.any { user.full.endsWith(it) }
    }

    /**
     * Determine whether a user id belongs to a bot admin.
     * @param[user] the user id to check
     * @return indicator for admin
     */
    fun isBotAdmin(user: UserId?): Boolean = user != null && admins.contains(user.full)
}
