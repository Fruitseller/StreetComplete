package de.westnordost.streetcomplete

import de.westnordost.streetcomplete.data.download.DownloadController
import de.westnordost.streetcomplete.data.maptiles.MapTilesDownloader
import de.westnordost.streetcomplete.data.osm.edits.upload.changesets.ChangesetAutoCloser
import de.westnordost.streetcomplete.data.osm.mapdata.BoundingBox
import de.westnordost.streetcomplete.data.upload.UploadController
import org.koin.dsl.module

/** iOS no-op implementations of the controllers that are WorkManager-backed on Android
 *  (bound there in `androidModule`). Real iOS implementations arrive with the map/sync
 *  milestone (M4). For now they satisfy the Koin graph so menu screens load without crashing:
 *  `SettingsViewModel -> Cleaner` reaches [MapTilesDownloader]; the other three are bound for
 *  graph completeness (only androidMain map/sync code consumes them today).
 *  Binding scopes mirror `androidModule` (single vs factory). */
val iosControllersModule = module {
    factory<MapTilesDownloader> { IosMapTilesDownloaderStub() }
    single<UploadController> { IosUploadControllerStub() }
    single<DownloadController> { IosDownloadControllerStub() }
    factory<ChangesetAutoCloser> { IosChangesetAutoCloserStub() }
}

private class IosMapTilesDownloaderStub : MapTilesDownloader {
    override suspend fun download(bbox: BoundingBox) { /* no-op until M4 */ }
    override suspend fun clear() { /* no-op until M4 */ }
}

private class IosUploadControllerStub : UploadController {
    override fun upload(isUserInitiated: Boolean) { /* no-op until M4 */ }
}

private class IosDownloadControllerStub : DownloadController {
    override fun download(bbox: BoundingBox, isUserInitiated: Boolean) { /* no-op until M4 */ }
}

private class IosChangesetAutoCloserStub : ChangesetAutoCloser {
    override fun enqueue(delayInMilliseconds: Long) { /* no-op until M4 */ }
}
