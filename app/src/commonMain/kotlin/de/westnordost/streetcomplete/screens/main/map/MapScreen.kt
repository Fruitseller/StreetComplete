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
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.data.preferences.Preferences
import de.westnordost.streetcomplete.screens.main.map2.Map
import de.westnordost.streetcomplete.ui.common.BackIcon
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.map
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.spatialk.geojson.Position

/** iOS map screen (M3b.2b): StreetComplete's own programmatic vector style, camera persisted via Preferences. */
@Composable
fun MapScreen(onClickBack: () -> Unit) {
    val viewModel: MapViewModel = koinViewModel()
    val downloadedTiles by viewModel.downloadedTiles.collectAsState()
    val markers by viewModel.geometryMarkers.collectAsState()
    val focusedGeometry by viewModel.focusedGeometry.collectAsState()

    val prefs: Preferences = koinInject()
    val cameraState = rememberCameraState(
        firstPosition = remember {
            if (prefs.mapZoom == 0.0) {
                // fresh install (no stored camera): a sensible zoomed-out default rather than mid-ocean at zoom 0
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
                println("map camera persisted: lat=${pos.target.latitude} lon=${pos.target.longitude} zoom=${pos.zoom}")
            }
    }

    Box(Modifier.fillMaxSize()) {
        Map(
            modifier = Modifier.fillMaxSize(),
            cameraState = cameraState,
            downloadedTiles = downloadedTiles,
            geometryMarkers = markers,
            focusedGeometry = focusedGeometry,
        )
        IconButton(
            onClick = onClickBack,
            modifier = Modifier.safeDrawingPadding().padding(8.dp),
        ) { BackIcon() }
    }
}
