package de.westnordost.streetcomplete.screens.main.map2

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.intl.Locale
import de.westnordost.streetcomplete.data.download.tiles.TilePos
import de.westnordost.streetcomplete.data.location.Location
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometry
import de.westnordost.streetcomplete.resources.Res
import de.westnordost.streetcomplete.screens.main.map2.layers.CurrentLocationLayers
import de.westnordost.streetcomplete.screens.main.map2.layers.DownloadedAreaLayer
import de.westnordost.streetcomplete.screens.main.map2.layers.FocusedGeometryLayers
import de.westnordost.streetcomplete.screens.main.map2.layers.GeometryMarkersLayers
import de.westnordost.streetcomplete.screens.main.map2.layers.Marker
import de.westnordost.streetcomplete.screens.main.map2.layers.Pin
import de.westnordost.streetcomplete.screens.main.map2.layers.PinsLayers
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.maplibre.compose.camera.CameraState
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.map.MapOptions
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.OrnamentOptions
import org.maplibre.compose.style.BaseStyle
import org.maplibre.compose.style.StyleState
import org.maplibre.compose.style.rememberStyleState

@Composable
fun Map(
    modifier: Modifier = Modifier,
    cameraState: CameraState = rememberCameraState(),
    styleState: StyleState = rememberStyleState(),
    downloadedTiles: Collection<TilePos> = emptyList(),
    geometryMarkers: Collection<Marker> = emptyList(),
    focusedGeometry: ElementGeometry? = null,
    pins: Collection<Pin> = emptyList(),
    location: Location? = null,
    rotation: Float? = null,
) {
    var styleEpoch by remember { mutableStateOf(0) }
    MaplibreMap(
        modifier = modifier,
        baseStyle = BaseStyle.Json(BASE_STYLE),
        zoomRange = 0f..22f,
        cameraState = cameraState,
        styleState = styleState,
        options = MapOptions(ornamentOptions = OrnamentOptions.AllDisabled),
        onMapLoadFinished = { styleEpoch++ },
    ) {
        val languages = listOf(Locale.current.language)
        val colors = if (isSystemInDarkTheme()) MapColors.Night else MapColors.Light
        MapStyle(
            colors = colors,
            languages = languages,
            belowLabelsContent = {
                // The downloaded-area layer hatches everything OUTSIDE the downloaded tiles. With no
                // downloaded tiles it would hatch the entire map (very prominent in dark mode and it
                // drowns the labels), so only show it once something is actually downloaded.
                if (downloadedTiles.isNotEmpty()) DownloadedAreaLayer(downloadedTiles)
            },
            aboveLabelsContent = {
                GeometryMarkersLayers(geometryMarkers)
                focusedGeometry?.let { FocusedGeometryLayers(it) }
                PinsLayers(pins)
                location?.let { CurrentLocationLayers(it, rotation) }
            },
        )
    }
    PinImageRegistry(styleState, styleEpoch, pins)
}

@OptIn(ExperimentalResourceApi::class)
private val BASE_STYLE =
    """
    {
      "version": 8,
      "name": "Empty",
      "metadata": {},
      "sources": {},
      "glyphs": "${
        Res.getUri("files/glyphs/Roboto Regular/0-255.pbf")
            .replace("Roboto Regular", "{fontstack}")
            .replace("0-255", "{range}")
    }",
      "layers": []
    }
    """.trimIndent()
