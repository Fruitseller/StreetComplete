package de.westnordost.streetcomplete

import de.westnordost.streetcomplete.data.download.DownloadController
import de.westnordost.streetcomplete.data.download.Downloader
import de.westnordost.streetcomplete.data.maptiles.MapTilesDownloader
import de.westnordost.streetcomplete.data.osm.edits.upload.changesets.ChangesetAutoCloser
import de.westnordost.streetcomplete.data.osm.mapdata.BoundingBox
import de.westnordost.streetcomplete.data.upload.UploadController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.core.qualifier.named
import org.koin.dsl.module

/** iOS implementations of the controllers that are WorkManager-backed on Android
 *  (bound there in `androidModule`). [IosDownloadController] drives the real shared
 *  [Downloader] on the app scope (M4.1); the remaining three are stubs until later milestones.
 *  Binding scopes mirror `androidModule` (single vs factory). */
val iosControllersModule = module {
    factory<MapTilesDownloader> { IosMapTilesDownloaderStub() }
    single<UploadController> { IosUploadControllerStub() }
    single<DownloadController> {
        IosDownloadController(get(), get(named("ApplicationScope")))
    }
    factory<ChangesetAutoCloser> { IosChangesetAutoCloserStub() }
}

private class IosMapTilesDownloaderStub : MapTilesDownloader {
    override suspend fun download(bbox: BoundingBox) { /* no-op until M4 */ }
    override suspend fun clear() { /* no-op until M4 */ }
}

private class IosUploadControllerStub : UploadController {
    override fun upload(isUserInitiated: Boolean) { /* no-op until M4 */ }
}

/** iOS DownloadController: bridges the non-suspend interface to the shared suspend Downloader
 *  on the app scope. Mirrors Android REPLACE/KEEP: a user-initiated (priority) download cancels
 *  the previous one; a non-user-initiated one is skipped while another is already running.
 *  The shared Downloader self-guards freshness and holds the SerializeSync mutex. */
private class IosDownloadController(
    private val downloader: Downloader,
    private val scope: CoroutineScope,
) : DownloadController {
    private var job: Job? = null
    override fun download(bbox: BoundingBox, isUserInitiated: Boolean) {
        if (isUserInitiated) job?.cancel()
        else if (job?.isActive == true) return
        job = scope.launch { downloader.download(bbox, isUserInitiated) }
    }
}

private class IosChangesetAutoCloserStub : ChangesetAutoCloser {
    override fun enqueue(delayInMilliseconds: Long) { /* no-op until M4 */ }
}
