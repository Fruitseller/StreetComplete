package de.westnordost.streetcomplete.screens.main.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.westnordost.streetcomplete.ApplicationConstants
import de.westnordost.streetcomplete.data.download.DownloadController
import de.westnordost.streetcomplete.data.download.tiles.DownloadedTilesSource
import de.westnordost.streetcomplete.data.download.tiles.TilePos
import de.westnordost.streetcomplete.data.download.tiles.TilesRect
import de.westnordost.streetcomplete.data.download.tiles.enclosingTilesRect
import de.westnordost.streetcomplete.data.location.Compass
import de.westnordost.streetcomplete.data.location.Location
import de.westnordost.streetcomplete.data.location.LocationSource
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.BoundingBox
import de.westnordost.streetcomplete.data.preferences.Preferences
import de.westnordost.streetcomplete.data.quest.QuestKey
import de.westnordost.streetcomplete.data.quest.QuestTypeRegistry
import de.westnordost.streetcomplete.data.quest.VisibleQuestsSource
import de.westnordost.streetcomplete.data.visiblequests.QuestTypeOrderSource
import de.westnordost.streetcomplete.screens.main.controls.LocationState
import de.westnordost.streetcomplete.screens.main.map2.QuestPinsManager
import de.westnordost.streetcomplete.screens.main.map2.layers.Marker
import de.westnordost.streetcomplete.screens.main.map2.layers.Pin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MapViewModel(
    private val downloadedTilesSource: DownloadedTilesSource,
    private val locationSource: LocationSource,
    private val compass: Compass,
    private val prefs: Preferences,
    private val downloadController: DownloadController,
    private val visibleQuestsSource: VisibleQuestsSource,
    private val questTypeRegistry: QuestTypeRegistry,
    private val questTypeOrderSource: QuestTypeOrderSource,
) : ViewModel() {

    private val _downloadedTiles = MutableStateFlow<List<TilePos>>(emptyList())
    val downloadedTiles: StateFlow<List<TilePos>> = _downloadedTiles.asStateFlow()

    private val _geometryMarkers = MutableStateFlow<List<Marker>>(emptyList())
    val geometryMarkers: StateFlow<List<Marker>> = _geometryMarkers.asStateFlow()

    private val _focusedGeometry = MutableStateFlow<ElementGeometry?>(null)
    val focusedGeometry: StateFlow<ElementGeometry?> = _focusedGeometry.asStateFlow()

    private val _pins = MutableStateFlow<List<Pin>>(emptyList())
    val pins: StateFlow<List<Pin>> = _pins.asStateFlow()

    private val viewport = MutableStateFlow<BoundingBox?>(null)
    private val questPinsManager = QuestPinsManager(
        viewport, visibleQuestsSource, questTypeRegistry, questTypeOrderSource, viewModelScope
    )

    // --- location ---
    val location: StateFlow<Location?> = locationSource.location

    private val _isFollowing = MutableStateFlow(prefs.mapIsFollowing)
    val isFollowing: StateFlow<Boolean> = _isFollowing.asStateFlow()

    val rotation: StateFlow<Float?> = compass.rotation

    private val _isNavigationMode = MutableStateFlow(prefs.mapIsNavigationMode)
    val isNavigationMode: StateFlow<Boolean> = _isNavigationMode.asStateFlow()

    private val _trackingRequested = MutableStateFlow(false)
    private var pendingFollowOnGrant = false

    /** DENIED until permission; ENABLED once permitted but not tracking; SEARCHING while waiting
     *  for the first fix; UPDATING once fixes arrive. (ALLOWED — permission granted but global
     *  Location Services off — is collapsed into DENIED: the button behaves identically.) */
    val locationState: StateFlow<LocationState> = combine(
        locationSource.hasPermission, _trackingRequested, locationSource.location
    ) { hasPermission, tracking, location ->
        when {
            !hasPermission -> LocationState.DENIED
            !tracking -> LocationState.ENABLED
            location == null -> LocationState.SEARCHING
            else -> LocationState.UPDATING
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LocationState.DENIED)

    private val downloadedTilesListener = object : DownloadedTilesSource.Listener {
        override fun onUpdated() { reloadDownloadedTiles() }
    }

    init {
        downloadedTilesSource.addListener(downloadedTilesListener)
        reloadDownloadedTiles()
        // Start tracking as soon as permission is held. On launch with permission already granted,
        // follow stays as restored from prefs; a fresh grant via the button also turns follow on
        // (mirrors Android onLocationIsEnabled).
        viewModelScope.launch {
            locationSource.hasPermission.collect { granted ->
                if (granted && !_trackingRequested.value) {
                    locationSource.start()
                    compass.start()
                    _trackingRequested.value = true
                    if (pendingFollowOnGrant) { setFollowing(true); pendingFollowOnGrant = false }
                }
            }
        }
        questPinsManager.start()
        viewModelScope.launch { questPinsManager.pins.collect { _pins.value = it } }
    }

    private fun reloadDownloadedTiles() {
        viewModelScope.launch {
            _downloadedTiles.value = withContext(Dispatchers.IO) {
                downloadedTilesSource.getAll(ApplicationConstants.DELETE_OLD_DATA_AFTER)
            }
        }
    }

    fun onClickLocationButton() {
        when {
            !locationState.value.isEnabled -> {
                pendingFollowOnGrant = true
                locationSource.requestPermission()
            }
            !isFollowing.value -> setFollowing(true)
            // navigation mode only makes sense once we're actually receiving fixes. Requiring UPDATING
            // also avoids a fast tap in the brief ENABLED window (permission held, tracking not yet
            // started) wrongly toggling navigation instead of (re-)following.
            locationState.value == LocationState.UPDATING -> setNavigationMode(!_isNavigationMode.value)
            else -> setFollowing(true)
        }
    }

    fun setFollowing(value: Boolean) {
        _isFollowing.value = value
        prefs.mapIsFollowing = value
    }

    fun setNavigationMode(value: Boolean) {
        _isNavigationMode.value = value
        prefs.mapIsNavigationMode = value
    }

    fun putGeometryMarkers(markers: List<Marker>) { _geometryMarkers.value = markers }
    fun clearGeometryMarkers() { _geometryMarkers.value = emptyList() }
    fun setFocusedGeometry(geometry: ElementGeometry?) { _focusedGeometry.value = geometry }
    fun setPins(pins: List<Pin>) { _pins.value = pins }

    // Last area requested for download this session — avoid re-downloading a contained area
    // (user-initiated downloads bypass the freshness check, so guard here).
    private var lastDownloadedRect: TilesRect? = null

    /** Called on camera-idle at zoom >= 14. Triggers a user-initiated download of the visible
     *  area unless that area is already covered this session, and drives the pins manager. */
    fun onViewportIdle(bbox: BoundingBox?) {
        if (bbox == null) return
        // Always drive the quest-pins manager (it does its own z16 windowing + dedup), so pins
        // refresh when panning within an already-downloaded area. Only the DOWNLOAD is dedup-guarded
        // below (user-initiated bypasses freshness, so guard against re-downloading a covered area).
        viewport.value = bbox
        val rect = bbox.enclosingTilesRect(16)
        if (lastDownloadedRect?.contains(rect) == true) return
        lastDownloadedRect = rect
        downloadController.download(bbox, isUserInitiated = true)
    }

    fun getQuestKey(properties: Map<String, String>): QuestKey? = questPinsManager.getQuestKey(properties)

    override fun onCleared() {
        questPinsManager.stop()
        downloadedTilesSource.removeListener(downloadedTilesListener)
        locationSource.stop()
        compass.stop()
    }
}
