package org.fuchss.matrix.bots

import net.folivo.trixnity.core.model.UserId

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
