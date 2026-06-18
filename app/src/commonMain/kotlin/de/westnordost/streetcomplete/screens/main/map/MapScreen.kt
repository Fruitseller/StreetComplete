package de.westnordost.streetcomplete.screens.main.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
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
import de.westnordost.streetcomplete.ui.common.BackIcon
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.map
import kotlin.time.Duration.Companion.milliseconds
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.maplibre.compose.camera.CameraMoveReason
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.rememberCameraState
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

    // Persist camera position when movement ends.
    LaunchedEffect(cameraState) {
        snapshotFlow { cameraState.isCameraMoving to cameraState.position }
            .filterNot { it.first }
            .map { it.second }
            .distinctUntilChanged()
            .collect { pos ->
                prefs.mapPosition = LatLon(latitude = pos.target.latitude, longitude = pos.target.longitude)
                prefs.mapZoom = pos.zoom
                prefs.mapRotation = pos.bearing
                prefs.mapTilt = pos.tilt
            }
    }

    // Follow mode: recenter on each fix; zoom to 18 on the first fix if zoomed out past 17.
    var zoomedYet by remember { mutableStateOf(false) }
    LaunchedEffect(isFollowing, location) {
        if (!isFollowing) { zoomedYet = false; return@LaunchedEffect }
        val loc = location ?: return@LaunchedEffect
        val current = cameraState.position
        val zoom = if (!zoomedYet && current.zoom < 17.0) 18.0 else current.zoom
        zoomedYet = true
        cameraState.animateTo(
            current.copy(
                target = Position(longitude = loc.position.longitude, latitude = loc.position.latitude),
                zoom = zoom,
            ),
            duration = 600.milliseconds,
        )
    }

    // User pan drops follow mode (programmatic follow animations report PROGRAMMATIC, not GESTURE).
    LaunchedEffect(Unit) {
        snapshotFlow { cameraState.moveReason }
            .distinctUntilChanged()
            .collect { reason ->
                if (reason == CameraMoveReason.GESTURE && viewModel.isFollowing.value) {
                    viewModel.setFollowing(false)
                }
            }
    }

    Box(Modifier.fillMaxSize()) {
        Map(
            modifier = Modifier.fillMaxSize(),
            cameraState = cameraState,
            downloadedTiles = downloadedTiles,
            geometryMarkers = markers,
            focusedGeometry = focusedGeometry,
            pins = pins,
            location = location,
            rotation = null, // heading arrow arrives in M3b.3b
        )
        IconButton(
            onClick = onClickBack,
            modifier = Modifier.safeDrawingPadding().padding(8.dp),
        ) { BackIcon() }
        LocationStateButton(
            onClick = viewModel::onClickLocationButton,
            state = locationState,
            isFollowing = isFollowing,
            modifier = Modifier.align(Alignment.BottomEnd).safeDrawingPadding().padding(16.dp),
        )
    }
}
