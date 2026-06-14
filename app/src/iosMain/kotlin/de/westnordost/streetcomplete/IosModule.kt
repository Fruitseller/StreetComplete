package de.westnordost.streetcomplete

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.ObservableSettings
import de.westnordost.streetcomplete.data.Database
import de.westnordost.streetcomplete.data.StreetCompleteDatabase
import de.westnordost.streetcomplete.resources.Res
import io.ktor.client.HttpClient
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.userAgent
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import org.koin.core.qualifier.named
import org.koin.dsl.module
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUserDomainMask

/** iOS counterpart of the Android `appModule` + `androidModule`: supplies the
 *  platform infrastructure (HTTP, file system, paths, database, settings) that
 *  the shared (commonMain) Koin graph depends on. */
val iosModule = module {
    // HTTP — same config as Android's appModule; ktor-client-darwin engine is auto-selected
    single {
        HttpClient {
            defaultRequest {
                userAgent(ApplicationConstants.USER_AGENT)
            }
            install(ContentEncoding) {
                gzip()
                identity()
            }
        }
    }

    // Resources & file system
    single<Res> { Res }
    single<FileSystem> { SystemFileSystem }

    // Database — same multiplatform driver as Android, file in the app's Documents dir
    // (WAL mode needs a persistently writable directory for the -wal/-shm sibling files)
    single<Database> {
        val dbPath = iosDirectory(NSDocumentDirectory) + "/" + ApplicationConstants.DATABASE_NAME
        StreetCompleteDatabase(BundledSQLiteDriver().open(dbPath))
    }

    // Avatars cache directory
    factory(named("AvatarsCacheDirectory")) {
        Path(iosDirectory(NSCachesDirectory), ApplicationConstants.AVATARS_CACHE_DIRECTORY)
    }

    // Settings
    single<ObservableSettings> { NSUserDefaultsSettings(NSUserDefaults.standardUserDefaults) }
}

/** Absolute path of an iOS user-domain directory (e.g. Documents, Caches). */
private fun iosDirectory(directory: platform.Foundation.NSSearchPathDirectory): String =
    NSSearchPathForDirectoriesInDomains(directory, NSUserDomainMask, true).first() as String
