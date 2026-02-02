# Matrix Bot Base

This repository contains an abstraction layer to build bots using [Trixnity](https://trixnity.gitlab.io/trixnity/).

I'm typically online in the [Trixnity channel](https://matrix.to/#/#trixnity:imbitbu.de). So feel free to tag me there if you have any questions.

## Usage

To use this library, you need to add it as a dependency to your project. You can do this by adding the following to your `pom.xml`:

```xml
<dependency>
    <groupId>org.fuchss</groupId>
    <artifactId>matrix-bot-base</artifactId>
    <version>VERSION</version>
</dependency>
```

This library contains helper classes to build bots.
Start by defining the creation of bot by creating a `Main.kt` file:

```kotlin
private lateinit var commands: List<Command>

fun main() {
    Backend.set(DefaultBackend)
    
    runBlocking {
        val config: IConfig = // Load config here 
            commands = listOf(HelpCommand(config, "FancyBot") { commands }, QuitCommand(), LogoutCommand(), ChangeUsernameCommand(), /* Custom commands here */)

        val matrixClient = getMatrixClient(config)

        val matrixBot = MatrixBot(matrixClient, config)
        matrixBot.subscribeContent { event -> handleCommand(commands, event, matrixBot, config) }
        matrixBot.subscribeContent { encryptedEvent -> handleEncryptedCommand(commands, encryptedEvent, matrixBot, config) }

        val loggedOut = matrixBot.startBlocking()

        // These lines will be reached if the bot shuts down
        if (loggedOut) {
            // Cleanup database stuff as you like (e.g., delete database files)
            val databaseFiles = listOf(File(config.dataDirectory + "/database.mv.db"), File(config.dataDirectory + "/database.trace.db"))
            databaseFiles.filter { it.exists() }.forEach { it.delete() }
        }
    }
}

private suspend fun getMatrixClient(config: Config): MatrixClient {
    val existingMatrixClient = MatrixClient.create(
        createRepositoriesModule(config),
        createMediaStoreModule(config),
        createCryptoDriverModule()
    ).getOrNull()
    if (existingMatrixClient != null) {
        return existingMatrixClient
    }

    val matrixClient = MatrixClient.create(
        createRepositoriesModule(config),
        createMediaStoreModule(config),
        createCryptoDriverModule(),
        MatrixClientAuthProviderData.classicLogin(
            baseUrl = Url(config.baseUrl),
            identifier = IdentifierType.User(config.username),
            password = config.password,
            initialDeviceDisplayName = "An interesting bot"
        ).getOrThrow()
    ).getOrThrow()

    return matrixClient
}
```

You also need a suitable configuration for your bot. You may use a class like this:

```kotlin
/**
 * This is the configuration template of the mensa bot.
 * @param[prefix] the command prefix the bot listens to. By default, "bot"
 * @param[baseUrl] the base url of the matrix server the bot shall use
 * @param[username] the username of the bot's account
 * @param[password] the password of the bot's account
 * @param[dataDirectory] the path to the databases and media folder
 * @param[admins] the matrix ids of the admins. E.g. "@user:invalid.domain"
 * @param[users] the matrix ids of the authorized users or servers. E.g. "@user:invalid.domain" or ":invalid.domain"
 */
data class Config(
    @JsonProperty override val prefix: String = "bot",
    @JsonProperty override val baseUrl: String,
    @JsonProperty override val username: String,
    @JsonProperty override val password: String,
    @JsonProperty override val dataDirectory: String,
    @JsonProperty override val admins: List<String>,
    @JsonProperty override val users: List<String>,
) : IConfig {
    companion object {
        private val log: Logger = LoggerFactory.getLogger(Config::class.java)

        /**
         * Load the config from the file path. You can set "CONFIG_PATH" in the environment to override the default location ("./config.json").
         */
        fun load(): Config {
            val configPath = System.getenv("CONFIG_PATH") ?: "./config.json"
            val configFile = File(configPath)
            if (!configFile.exists()) {
                error("Config ${configFile.absolutePath} does not exist!")
            }

            val config: Config = ObjectMapper().registerKotlinModule().readValue(configFile)
            log.info("Loaded config ${configFile.absolutePath}")
            config.validate()
            return config
        }
    }
}
```

This creates a bot that can be used to handle text messages. The bot will automatically handle the following commands:

* `help` - Shows a list of all available commands
* `quit` - Quits the bot
* `logout` - Quits the bot and logs out all sessions
* `name {username}` - Changes the username of the bot
