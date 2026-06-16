package de.westnordost.streetcomplete.screens.main.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.westnordost.streetcomplete.ApplicationConstants
import de.westnordost.streetcomplete.data.download.tiles.DownloadedTilesSource
import de.westnordost.streetcomplete.data.download.tiles.TilePos
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometry
import de.westnordost.streetcomplete.screens.main.map2.layers.Marker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MapViewModel(
    private val downloadedTilesSource: DownloadedTilesSource,
) : ViewModel() {

    private val _downloadedTiles = MutableStateFlow<List<TilePos>>(emptyList())
    val downloadedTiles: StateFlow<List<TilePos>> = _downloadedTiles.asStateFlow()

    private val _geometryMarkers = MutableStateFlow<List<Marker>>(emptyList())
    val geometryMarkers: StateFlow<List<Marker>> = _geometryMarkers.asStateFlow()

    private val _focusedGeometry = MutableStateFlow<ElementGeometry?>(null)
    val focusedGeometry: StateFlow<ElementGeometry?> = _focusedGeometry.asStateFlow()

    private val downloadedTilesListener = object : DownloadedTilesSource.Listener {
        override fun onUpdated() { reloadDownloadedTiles() }
    }

    init {
        downloadedTilesSource.addListener(downloadedTilesListener)
        reloadDownloadedTiles()
    }

    private fun reloadDownloadedTiles() {
        viewModelScope.launch {
            // getAll is a blocking DB read → off the main thread (parity with androidMain DownloadedAreaManager)
            _downloadedTiles.value = withContext(Dispatchers.IO) {
                downloadedTilesSource.getAll(ApplicationConstants.DELETE_OLD_DATA_AFTER)
            }
        }
    }

    fun putGeometryMarkers(markers: List<Marker>) { _geometryMarkers.value = markers }
    fun clearGeometryMarkers() { _geometryMarkers.value = emptyList() }
    fun setFocusedGeometry(geometry: ElementGeometry?) { _focusedGeometry.value = geometry }

    override fun onCleared() {
        downloadedTilesSource.removeListener(downloadedTilesListener)
    }
}
