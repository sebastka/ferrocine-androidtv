package io.github.sebastka.ferrocine.player

import android.content.Context
import android.hardware.display.DisplayManager
import android.media.MediaCodecList
import android.view.Display
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioCapabilities
import io.github.sebastka.ferrocine.api.Capabilities

/**
 * What this TV plays as is, in the server's names (ffprobe codec names): the
 * decoders it has, the audio formats its HDMI sink takes as passthrough, and
 * the HDR formats its display shows. Probed at each playback, as the sink
 * (a soundbar, an AVR) may change.
 */
@OptIn(UnstableApi::class)
object DeviceCapabilities {
    /** Containers Media3's extractors read. */
    private val CONTAINERS = listOf("mp4", "mkv", "webm", "ts")

    private val VIDEO = listOf(
        "h264" to "video/avc",
        "hevc" to "video/hevc",
        "vp9" to "video/x-vnd.on2.vp9",
        "vp8" to "video/x-vnd.on2.vp8",
        "av1" to "video/av01",
        "mpeg2video" to "video/mpeg2",
        "mpeg4" to "video/mp4v-es",
    )

    private val AUDIO = listOf(
        "aac" to "audio/mp4a-latm",
        "mp3" to "audio/mpeg",
        "opus" to "audio/opus",
        "vorbis" to "audio/vorbis",
        "flac" to "audio/flac",
        "ac3" to "audio/ac3",
        "eac3" to "audio/eac3",
        "dts" to "audio/vnd.dts",
        "truehd" to "audio/true-hd",
    )

    /** Formats an HDMI sink may take undecoded. */
    private val PASSTHROUGH = listOf(
        "ac3" to C.ENCODING_AC3,
        "eac3" to C.ENCODING_E_AC3,
        "dts" to C.ENCODING_DTS,
        "truehd" to C.ENCODING_DOLBY_TRUEHD,
    )

    /** PCM in Matroska and MP4 needs no decoder. */
    private val PCM = listOf("pcm_s16le", "pcm_s24le", "pcm_s16be", "pcm_f32le")

    fun probe(context: Context): Capabilities {
        val decoders = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
            .filter { !it.isEncoder }
            .flatMap { it.supportedTypes.map(String::lowercase) }
            .toSet()
        val video = VIDEO.filter { it.second in decoders }.map { it.first }
        val sink = AudioCapabilities.getCapabilities(context, AudioAttributes.DEFAULT, null, emptyList())
        val passthrough = PASSTHROUGH.filter { sink.supportsEncoding(it.second) }.map { it.first }
        val audio = (AUDIO.filter { it.second in decoders }.map { it.first } + passthrough + PCM).distinct()
        return Capabilities(
            containers = CONTAINERS,
            videoCodecs = video,
            audioCodecs = audio,
            hdr = hdr(context, decoders),
            selectsTracks = true,
        )
    }

    @Suppress("DEPRECATION")
    private fun hdr(context: Context, decoders: Set<String>): List<String> {
        val display = context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)
            ?: return emptyList()
        val types = display.hdrCapabilities?.supportedHdrTypes ?: return emptyList()
        return buildList {
            if (Display.HdrCapabilities.HDR_TYPE_HDR10 in types || Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS in types) {
                add("hdr10")
            }
            if (Display.HdrCapabilities.HDR_TYPE_HLG in types) add("hlg")
            if (Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION in types && "video/dolby-vision" in decoders) {
                add("dolby_vision")
            }
        }
    }
}
