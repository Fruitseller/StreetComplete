package de.westnordost.streetcomplete.screens.main.map

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.Position

/** First iOS map (M3b.1): a real vector map reachable from the menu. Online-only for now;
 *  pins/overlays/location come in later M3b increments. */
@Composable
fun MapScreen() {
    val cameraState = rememberCameraState(
        firstPosition = CameraPosition(
            target = Position(longitude = 13.4, latitude = 52.5), // Berlin
            zoom = 12.0,
        )
    )
    MaplibreMap(
        modifier = Modifier.fillMaxSize(),
        baseStyle = BaseStyle.Uri(JAWG_STREETS_STYLE_URL),
        cameraState = cameraState,
    )
}

// JawgMaps hosted vector style (same provider/token the Android build + PR #6352 use).
// TODO M3b: externalize this token out of source.
private const val JAWG_STREETS_STYLE_URL =
    "https://api.jawg.io/styles/jawg-streets.json?access-token=" +
        "mL9X4SwxfsAGfojvGiion9hPKuGLKxPbogLyMbtakA2gJ3X88gcVlTSQ7OD6OfbZ"
