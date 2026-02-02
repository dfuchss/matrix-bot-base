package org.fuchss.matrix.bots.helper

import de.connect2x.trixnity.client.CryptoDriverModule
import de.connect2x.trixnity.client.MediaStoreModule
import de.connect2x.trixnity.client.RepositoriesModule
import de.connect2x.trixnity.client.cryptodriver.vodozemac.vodozemac
import de.connect2x.trixnity.client.media.okio.okio
import de.connect2x.trixnity.client.store.repository.exposed.exposed
import okio.Path.Companion.toOkioPath
import org.fuchss.matrix.bots.IConfig
import org.jetbrains.exposed.sql.Database
import java.io.File

/**
 * Create a repositories module for the bot using an H2 database.
 * @param config The bot configuration containing the data directory path
 * @return A configured [RepositoriesModule] using the exposed driver
 */
fun createRepositoriesModule(config: IConfig) =
    RepositoriesModule.exposed(database = Database.connect("jdbc:h2:${config.dataDirectory}/database;DB_CLOSE_DELAY=-1"))

/**
 * Create a media store module for the bot using the local file system.
 * @param config The bot configuration containing the data directory path
 * @return A configured [MediaStoreModule] using okio for file operations
 */
fun createMediaStoreModule(config: IConfig) = MediaStoreModule.okio(File(config.dataDirectory + "/media").toOkioPath())

/**
 * Create a crypto driver module for the bot using the vodozemac crypto library.
 * @return A configured [CryptoDriverModule] for end-to-end encryption
 */
fun createCryptoDriverModule() = CryptoDriverModule.vodozemac()
