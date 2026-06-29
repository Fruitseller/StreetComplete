package de.westnordost.streetcomplete.screens.main.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import de.westnordost.streetcomplete.screens.main.map2.toGeoJsonBoundingBox
import androidx.compose.material.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.data.preferences.Preferences
import de.westnordost.streetcomplete.screens.main.controls.LocationStateButton
import de.westnordost.streetcomplete.screens.main.map2.Map
import de.westnordost.streetcomplete.screens.main.map2.toBoundingBox
import de.westnordost.streetcomplete.ui.common.BackIcon
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.map
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Duration.Companion.milliseconds
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.maplibre.compose.camera.CameraMoveReason
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.util.ClickResult
import org.maplibre.compose.util.FeaturesClickHandler
import org.maplibre.compose.util.MapClickHandler
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position

/** iOS map screen (M3b.3a): live location dot + Android-parity GPS button (request → follow). */
@Composable
fun MapScreen(onClickBack: () -> Unit) {
    val viewModel: MapViewModel = koinViewModel()
    val downloadedTiles by viewModel.downloadedTiles.collectAsState()
    val markers by viewModel.geometryMarkers.collectAsState()
    val focusedGeometry by viewModel.focusedGeometry.collectAsState()
    val pins by viewModel.pins.collectAsState()
    val location by viewModel.location.collectAsState()
    val locationState by viewModel.locationState.collectAsState()
    val isFollowing by viewModel.isFollowing.collectAsState()
    val rotation by viewModel.rotation.collectAsState()
    val isNavigationMode by viewModel.isNavigationMode.collectAsState()
    val selectedQuest by viewModel.selectedQuest.collectAsState()

    val prefs: Preferences = koinInject()
    val cameraState = rememberCameraState(
        firstPosition = remember {
            if (prefs.mapZoom == 0.0) {
                CameraPosition(target = Position(longitude = 13.4, latitude = 52.5), zoom = 3.0)
            } else {
                CameraPosition(
                    target = Position(longitude = prefs.mapPosition.longitude, latitude = prefs.mapPosition.latitude),
                    zoom = prefs.mapZoom,
                    bearing = prefs.mapRotation,
                    tilt = prefs.mapTilt,
                )
            }
        }
    )

    // Persist camera position when a USER-driven move ends. Skip while following: follow animates the
    // camera ~1×/fix, and persisting those would churn prefs and store tilt=60 / GPS heading from
    // navigation mode (so a later cold start would restore a tilted, rotated camera). A user pan flips
    // isFollowing off (via the pan detector below) before the move ends, so real pans still persist.
    LaunchedEffect(cameraState) {
        snapshotFlow { cameraState.isCameraMoving to cameraState.position }
            .filterNot { it.first }
            .map { it.second }
            .distinctUntilChanged()
            .collect { pos ->
                if (viewModel.isFollowing.value || viewModel.selectedQuest.value != null) return@collect
                prefs.mapPosition = LatLon(latitude = pos.target.latitude, longitude = pos.target.longitude)
                prefs.mapZoom = pos.zoom
                prefs.mapRotation = pos.bearing
                prefs.mapTilt = pos.tilt
            }
    }

    // Follow mode: recenter on each fix; zoom to 18 on the first fix if zoomed out past 17.
    var zoomedYet by remember { mutableStateOf(false) }
    LaunchedEffect(isFollowing, isNavigationMode, location) {
        if (!isFollowing) { zoomedYet = false; return@LaunchedEffect }
        val loc = location ?: return@LaunchedEffect
        val current = cameraState.position
        val zoom = if (!zoomedYet && current.zoom < 17.0) 18.0 else current.zoom
        cameraState.animateTo(
            current.copy(
                target = Position(longitude = loc.position.longitude, latitude = loc.position.latitude),
                zoom = zoom,
                // navigation mode: rotate the map to the GPS course + tilt; normal mode keeps bearing, no tilt.
                // Exiting navigation mode keeps the last bearing (matches Android) and drops the tilt.
                bearing = if (isNavigationMode) (loc.bearing?.toDouble() ?: current.bearing) else current.bearing,
                tilt = if (isNavigationMode) 60.0 else 0.0,
            ),
            duration = 600.milliseconds,
        )
        // set only AFTER the animation completes: if a new fix cancels it mid-zoom, zoomedYet stays
        // false so the next fix re-attempts the first-fix zoom-to-18 instead of locking in an
        // intermediate zoom read from the cancelled animation.
        zoomedYet = true
    }

    // User pan drops follow mode (programmatic follow animations report PROGRAMMATIC, not GESTURE).
    LaunchedEffect(cameraState) {
        snapshotFlow { cameraState.moveReason }
            .distinctUntilChanged()
            .collect { reason ->
                if (reason == CameraMoveReason.GESTURE && viewModel.isFollowing.value) {
                    viewModel.setFollowing(false)
                }
            }
    }

    // Trigger a download (and, from M4.2, quest-pin fetch) of the visible area on camera-idle at
    // zoom >= 14. Read projection in the collect (it is not Compose snapshot state); trigger on
    // position/isCameraMoving (which are). Emit only at zoom >= 14; retain below so clusters stay.
    LaunchedEffect(cameraState) {
        snapshotFlow {
            Triple(cameraState.isCameraMoving, cameraState.position.zoom, cameraState.position.target)
        }
            .filterNot { it.first }
            .filter { it.second >= 14.0 }
            .distinctUntilChanged()
            .collect {
                viewModel.onViewportIdle(
                    cameraState.projection?.queryVisibleBoundingBox()?.toBoundingBox()
                )
            }
    }

    // The snapshotFlow above can miss the very first idle (projection is null until the map has
    // laid out, and it isn't Compose snapshot state so it doesn't re-emit). Await the projection
    // once on open and fire an initial viewport update, so data downloads (and, from M4.2, quest
    // pins load) on a static map open without requiring a pan.
    LaunchedEffect(cameraState) {
        val projection = cameraState.awaitProjection()
        if (cameraState.position.zoom >= 14.0) {
            viewModel.onViewportIdle(projection.queryVisibleBoundingBox().toBoundingBox())
        }
    }

    val onClickPin: FeaturesClickHandler = handler@{ features ->
        val props = features.firstOrNull()?.properties.orEmpty()
            .mapNotNull { (k, v) -> (v as? JsonPrimitive)?.let { k to it.content } }
            .toMap()
        val questKey = viewModel.getQuestKey(props)
        if (questKey != null) {
            viewModel.selectQuest(questKey)
            ClickResult.Consume
        } else {
            ClickResult.Pass
        }
    }
    val onMapClick: MapClickHandler = { _, _ ->
        if (viewModel.selectedQuest.value != null) {
            viewModel.clearSelection()
            ClickResult.Consume
        } else {
            ClickResult.Pass
        }
    }
    val scope = rememberCoroutineScope()
    // No cluster-leaf query API on iOS (maplibre-compose 0.13.0 has no getClusterLeaves /
    // querySourceFeatures), so we cannot fit the leaves' bbox like Android's zoomToCluster. Fallback:
    // zoom in toward the cluster point past CLUSTER_MAX_ZOOM (=14), which dissolves it into pins.
    val onClickCluster: FeaturesClickHandler = handler@{ features ->
        val point = features.firstOrNull()?.geometry as? Point ?: return@handler ClickResult.Pass
        val zoom = minOf(cameraState.position.zoom + 2.0, 15.0)
        scope.launch {
            cameraState.animateTo(CameraPosition(target = point.coordinates, zoom = zoom))
        }
        ClickResult.Consume
    }

    // Bottom padding the camera reserves below the focused geometry. 0 for now; when the quest-form
    // bottom sheet lands (upstream-blocked), pass PaddingValues(bottom = sheetHeight) here.
    val focusPadding = PaddingValues(bottom = 0.dp)

    // Camera focus on the selected geometry; restore the pre-selection camera on deselect.
    var savedCamera by remember { mutableStateOf<CameraPosition?>(null) }
    LaunchedEffect(selectedQuest) {
        val sel = selectedQuest
        if (sel != null) {
            // capture once per selection CHAIN — not on reselect — so deselect returns to the origin.
            if (savedCamera == null) savedCamera = cameraState.position
            cameraState.animateTo(
                boundingBox = sel.geometry.bounds.toGeoJsonBoundingBox(),
                padding = focusPadding,
                duration = 500.milliseconds,
            )
        } else {
            savedCamera?.let { cameraState.animateTo(it, duration = 500.milliseconds) }
            savedCamera = null
        }
    }

    Box(Modifier.fillMaxSize()) {
        Map(
            modifier = Modifier.fillMaxSize(),
            cameraState = cameraState,
            downloadedTiles = downloadedTiles,
            geometryMarkers = markers,
            focusedGeometry = selectedQuest?.geometry ?: focusedGeometry,
            pins = pins,
            selectedQuest = selectedQuest,
            location = location,
            rotation = rotation?.let { (it - cameraState.position.bearing.toFloat()) },
            onClickPin = onClickPin,
            onClickCluster = onClickCluster,
            onMapClick = onMapClick,
        )
        // TODO(M4.x quest form): when the upstream compose-quest-form path unblocks (OsmQuestFormContainer
        //  commit handlers are currently all TODO()), mount it in a ModalBottomSheet here, driven by
        //  `selectedQuest`, and pass the sheet height into `focusPadding` above so the geometry sits
        //  above the sheet (= Android getQuestFormInsets()). No selection/highlight code changes needed.
        IconButton(
            onClick = onClickBack,
            modifier = Modifier.safeDrawingPadding().padding(8.dp),
        ) { BackIcon() }
        LocationStateButton(
            onClick = viewModel::onClickLocationButton,
            state = locationState,
            // only highlight "following" when location is actually enabled — otherwise a fresh install
            // (DENIED + persisted isFollowing=true) shows the disabled icon misleadingly tinted active.
            isFollowing = isFollowing && locationState.isEnabled,
            isNavigationMode = isNavigationMode,
            modifier = Modifier.align(Alignment.BottomEnd).safeDrawingPadding().padding(16.dp),
        )
    }
}
