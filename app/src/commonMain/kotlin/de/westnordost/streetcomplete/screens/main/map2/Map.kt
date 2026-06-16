package de.westnordost.streetcomplete.screens.main.map2

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.intl.Locale
import de.westnordost.streetcomplete.resources.Res
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
) {
    MaplibreMap(
        modifier = modifier,
        baseStyle = BaseStyle.Json(BASE_STYLE),
        zoomRange = 0f..22f,
        cameraState = cameraState,
        styleState = styleState,
        options = MapOptions(ornamentOptions = OrnamentOptions.AllDisabled),
    ) {
        val languages = listOf(Locale.current.language)
        val colors = if (isSystemInDarkTheme()) MapColors.Night else MapColors.Light
        MapStyle(colors = colors, languages = languages)
    }
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
