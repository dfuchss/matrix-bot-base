package org.fuchss.matrix.bots

import de.connect2x.lognity.api.backend.Backend
import de.connect2x.lognity.backend.DefaultBackend
import de.connect2x.trixnity.client.MatrixClient
import de.connect2x.trixnity.client.create
import de.connect2x.trixnity.clientserverapi.client.MatrixClientAuthProviderData
import de.connect2x.trixnity.clientserverapi.client.classicLogin
import de.connect2x.trixnity.clientserverapi.model.authentication.IdentifierType
import de.connect2x.trixnity.core.MatrixServerException
import io.ktor.http.Url
import kotlinx.coroutines.runBlocking
import org.fuchss.matrix.bots.command.ChangeUsernameCommand
import org.fuchss.matrix.bots.command.LogoutCommand
import org.fuchss.matrix.bots.command.QuitCommand
import org.fuchss.matrix.bots.helper.createCryptoDriverModule
import org.fuchss.matrix.bots.helper.createMediaStoreModule
import org.fuchss.matrix.bots.helper.createRepositoriesModule
import org.fuchss.matrix.bots.helper.handleCommand
import org.fuchss.matrix.bots.helper.handleEncryptedCommand
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

class MatrixBotTest {
    @BeforeEach
    fun cleanup() {
        Backend.set(DefaultBackend)
        // Clean up the target directory before each test
        val targetDir = File("target/data")
        if (targetDir.exists()) {
            targetDir.deleteRecursively()
        }
        targetDir.mkdirs()
    }

    @Test
    fun testStart() {
        val exception =
            assertThrows(MatrixServerException::class.java) {
                runBlocking {
                    val config: IConfig =
                        object : IConfig {
                            override val prefix: String = "!"
                            override val baseUrl: String = "https://matrix-client.matrix.org"
                            override val username: String = "invalid-user-name-1337133713371337"
                            override val password: String = "invalid-user-name-1337133713371337"
                            override val dataDirectory: String = "./target/data"
                            override val admins: List<String> = listOf("@invalid:invalid.invalid")
                            override val users: List<String> = listOf()
                        }

                    val commands = listOf(QuitCommand(), LogoutCommand(), ChangeUsernameCommand())

                    val matrixClient = getMatrixClient(config)

                    val matrixBot = MatrixBot(matrixClient, config)
                    matrixBot.subscribeContent { event -> handleCommand(commands, event, matrixBot, config) }
                    matrixBot.subscribeContent { encryptedEvent -> handleEncryptedCommand(commands, encryptedEvent, matrixBot, config) }

                    matrixBot.startBlocking()
                }
            }

        assertEquals("statusCode=403 Forbidden errorResponse=Forbidden(error=Invalid username/password) retryAfter=null", exception.message)
    }

    private suspend fun getMatrixClient(config: IConfig): MatrixClient {
        val existingMatrixClient = MatrixClient.create(createRepositoriesModule(config), createMediaStoreModule(config), createCryptoDriverModule()).getOrNull()
        if (existingMatrixClient != null) {
            return existingMatrixClient
        }

        val matrixClient =
            MatrixClient
                .create(
                    createRepositoriesModule(config),
                    createMediaStoreModule(config),
                    createCryptoDriverModule(),
                    MatrixClientAuthProviderData
                        .classicLogin(
                            baseUrl = Url(config.baseUrl),
                            identifier = IdentifierType.User(config.username),
                            password = config.password,
                            initialDeviceDisplayName = "CI Test"
                        ).getOrThrow()
                ).getOrThrow()

        return matrixClient
    }
}
