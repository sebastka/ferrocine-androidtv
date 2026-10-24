package io.github.sebastka.ferrocine.player

import io.github.sebastka.ferrocine.api.AudioStream
import io.github.sebastka.ferrocine.api.Chapter
import io.github.sebastka.ferrocine.api.Item
import io.github.sebastka.ferrocine.api.Media
import io.github.sebastka.ferrocine.api.SubtitleStream
import io.github.sebastka.ferrocine.api.Trickplay

/**
 * Where an HLS stream sits in the item: stream time 0 is item time
 * [offsetMs], and the server has produced up to [availableMs] of stream so
 * far. A direct play has offset 0 and the whole file.
 */
data class Window(val offsetMs: Long, val availableMs: Long, val hls: Boolean) {
    /**
     * The stream position to seek to for item time [itemMs], or `null` when
     * it lies outside what the stream holds (a new playback starts there).
     * The last seconds of an HLS window are not trusted: the segment may be
     * in the making.
     */
    fun streamPosition(itemMs: Long): Long? {
        if (!hls) return itemMs.coerceAtLeast(0)
        val pos = itemMs - offsetMs
        return if (pos >= 0 && pos <= availableMs - 4_000) pos else null
    }
}

/** Where frame [n] of a preview set is: sheet (0-based), column, row. */
data class Tile(val sheet: Int, val column: Int, val row: Int)

fun Trickplay.tileAt(itemMs: Long): Tile? {
    if (thumbnails <= 0 || intervalMs <= 0) return null
    val n = (itemMs / intervalMs).toInt().coerceIn(0, thumbnails - 1)
    val perSheet = columns * rows
    val sheet = n / perSheet
    if (sheet >= sheets.size) return null
    return Tile(sheet, n % columns, (n / columns) % rows)
}

/** The chapter playing at [itemMs]. */
fun List<Chapter>.at(itemMs: Long): Chapter? = lastOrNull { it.startMs <= itemMs }

/** How far one press of left/right moves while scrubbing: longer as the key is held. */
fun scrubStep(repeat: Int): Long = when {
    repeat < 6 -> 10_000
    repeat < 16 -> 30_000
    else -> 60_000
}

/** The audio track to start with: the default one, else the first. */
fun Media.defaultAudio(): AudioStream? = audio.firstOrNull { it.default } ?: audio.firstOrNull()

/** Subtitles the player can show: text ones, and bitmap ones in the file (burned in when transcoding). */
fun Media.selectableSubtitles(): List<SubtitleStream> = subtitles.filter { it.text || !it.external }

/** Subtitles inside the file, in the order the player's demuxer lists them. */
fun Media.embeddedSubtitles(): List<SubtitleStream> = subtitles.filter { !it.external && it.index < 1000 }.sortedBy { it.index }

/** The position of [audio] among the file's audio tracks (the player's order). */
fun Media.audioOrdinal(audio: AudioStream): Int = this.audio.sortedBy { it.index }.indexOfFirst { it.index == audio.index }

/**
 * How a subtitle is shown: from the file by the player (direct play), from
 * the server as WebVTT, or burned into the picture by the server.
 */
enum class SubtitleWay { Embedded, Sideloaded, Burned }

fun subtitleWay(s: SubtitleStream, hls: Boolean): SubtitleWay = when {
    !s.text -> if (hls) SubtitleWay.Burned else SubtitleWay.Embedded
    s.external || s.index >= 1000 -> SubtitleWay.Sideloaded
    hls -> SubtitleWay.Sideloaded
    else -> SubtitleWay.Embedded
}

/** Track labels: "English · Surround · EAC3 5.1". */
fun AudioStream.label(): String = listOfNotNull(
    language?.let(::languageName),
    title,
    codec.uppercase() + (channels?.let { " " + channelLayout(it) } ?: ""),
).distinct().joinToString(" · ")

fun SubtitleStream.label(): String = listOfNotNull(
    language?.let(::languageName) ?: "Unknown",
    title,
    if (forced) "Forced" else null,
    if (index >= 1_000_000) "Downloaded" else if (external) "External" else null,
    if (!text) "Image" else null,
).distinct().joinToString(" · ")

private fun channelLayout(n: Int): String = when (n) {
    1 -> "mono"
    2 -> "stereo"
    6 -> "5.1"
    8 -> "7.1"
    else -> "${n}ch"
}

fun languageName(code: String): String {
    val l = java.util.Locale.forLanguageTag(code)
    val name = l.getDisplayLanguage(java.util.Locale.getDefault())
    return if (name.isNullOrBlank() || name.equals(code, ignoreCase = true)) code else name.replaceFirstChar { it.uppercase() }
}

/** The episode after [current] among its season's [episodes]. */
fun nextEpisode(current: Item, episodes: List<Item>): Item? {
    val i = episodes.indexOfFirst { it.id == current.id }
    return if (i >= 0) episodes.getOrNull(i + 1) else null
}
