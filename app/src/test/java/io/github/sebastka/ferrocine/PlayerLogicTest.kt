package io.github.sebastka.ferrocine

import io.github.sebastka.ferrocine.api.AudioStream
import io.github.sebastka.ferrocine.api.Chapter
import io.github.sebastka.ferrocine.api.Item
import io.github.sebastka.ferrocine.api.Media
import io.github.sebastka.ferrocine.api.SubtitleStream
import io.github.sebastka.ferrocine.api.Trickplay
import io.github.sebastka.ferrocine.player.SubtitleWay
import io.github.sebastka.ferrocine.player.Tile
import io.github.sebastka.ferrocine.player.Window
import io.github.sebastka.ferrocine.player.at
import io.github.sebastka.ferrocine.player.audioOrdinal
import io.github.sebastka.ferrocine.player.defaultAudio
import io.github.sebastka.ferrocine.player.embeddedSubtitles
import io.github.sebastka.ferrocine.player.nextEpisode
import io.github.sebastka.ferrocine.player.scrubStep
import io.github.sebastka.ferrocine.player.selectableSubtitles
import io.github.sebastka.ferrocine.player.subtitleWay
import io.github.sebastka.ferrocine.player.tileAt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerLogicTest {
    @Test
    fun hlsWindows() {
        val w = Window(offsetMs = 600_000, availableMs = 120_000, hls = true)
        assertEquals(30_000L, w.streamPosition(630_000))
        // Before the stream's start, or beyond what is produced: a new playback.
        assertNull(w.streamPosition(500_000))
        assertNull(w.streamPosition(719_000))
        assertEquals(900_000L, Window(0, 0, hls = false).streamPosition(900_000))
    }

    @Test
    fun trickplayTiles() {
        val t = Trickplay(10_000, 240, 136, 10, 10, 642, List(7) { "/s/${it + 1}.jpg" })
        assertEquals(Tile(0, 0, 0), t.tileAt(0))
        assertEquals(Tile(0, 3, 1), t.tileAt(135_000))
        assertEquals(Tile(1, 0, 0), t.tileAt(1_000_000))
        // Past the end: the last frame.
        assertEquals(Tile(6, 1, 4), t.tileAt(99_000_000))
        assertNull(t.copy(sheets = emptyList()).tileAt(0))
    }

    @Test
    fun chaptersAndSteps() {
        val ch = listOf(Chapter(0, "Opening"), Chapter(60_000, "Heist"))
        assertEquals("Heist", ch.at(61_000)?.title)
        assertEquals("Opening", ch.at(0)?.title)
        assertEquals(10_000L, scrubStep(0))
        assertEquals(60_000L, scrubStep(40))
    }

    @Test
    fun tracks() {
        val media = Media(
            container = "mkv",
            audio = listOf(AudioStream(2, "ac3"), AudioStream(1, "eac3", default = true)),
            subtitles = listOf(
                SubtitleStream(4, "hdmv_pgs_subtitle", text = false),
                SubtitleStream(3, "subrip"),
                SubtitleStream(1000, "subrip", external = true),
                SubtitleStream(1001, "dvd_subtitle", external = true, text = false),
                SubtitleStream(1_000_000, "subrip", external = true),
            ),
        )
        assertEquals(1, media.defaultAudio()?.index)
        assertEquals(1, media.audioOrdinal(media.audio[0]))
        assertEquals(listOf(3, 4), media.embeddedSubtitles().map { it.index })
        // An external bitmap subtitle cannot be shown.
        assertEquals(listOf(4, 3, 1000, 1_000_000), media.selectableSubtitles().map { it.index })
        val (pgs, srt, ext) = media.subtitles
        assertEquals(SubtitleWay.Embedded, subtitleWay(pgs, hls = false))
        assertEquals(SubtitleWay.Burned, subtitleWay(pgs, hls = true))
        assertEquals(SubtitleWay.Embedded, subtitleWay(srt, hls = false))
        assertEquals(SubtitleWay.Sideloaded, subtitleWay(srt, hls = true))
        assertEquals(SubtitleWay.Sideloaded, subtitleWay(ext, hls = false))
    }

    @Test
    fun nextEpisodes() {
        val eps = (1..3).map { Item(id = it.toLong(), kind = "episode", title = "E$it") }
        assertEquals(2L, nextEpisode(eps[0], eps)?.id)
        assertNull(nextEpisode(eps[2], eps))
    }
}
