package de.westnordost.streetcomplete.data

import kotlinx.io.Source
import kotlinx.io.readString
import nl.adaptivity.xmlutil.XmlReader
import nl.adaptivity.xmlutil.xmlStreaming

/** Create an [XmlReader] over the full UTF-8 content of [source].
 *
 *  Use this instead of xmlutil's `nl.adaptivity.xmlutil.core.kxio.newReader(Source)`: that
 *  Source-based reader mis-decodes the byte stream on Kotlin/Native, so parsing the OSM API XML
 *  died at the first multibyte UTF-8 character (e.g. "ö") with "End of document before end of
 *  document element", and on larger map-data responses with an out-of-range buffer index. Reading
 *  the whole body into a UTF-8 [String] first and using the [CharSequence] reader decodes
 *  correctly on all targets. The OSM API responses parsed here are bounded (the downloader windows
 *  requests by z16 tiles and the API caps elements per request), so buffering them is safe. */
fun xmlReader(source: Source): XmlReader =
    xmlStreaming.newReader(source.readString())
