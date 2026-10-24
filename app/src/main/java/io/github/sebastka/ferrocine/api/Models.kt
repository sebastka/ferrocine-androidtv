package io.github.sebastka.ferrocine.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// The core's JSON API (docs/api.md in the core repository). Fields the app
// does not use are left out; unknown fields are ignored.

@Serializable
data class User(
    val id: Long,
    val username: String,
    @SerialName("display_name") val displayName: String? = null,
    val admin: Boolean = false,
) {
    val shownName: String get() = displayName ?: username
}

@Serializable
data class Device(
    val id: Long,
    val name: String,
    val platform: String? = null,
    @SerialName("created_at") val createdAt: Long = 0,
    @SerialName("last_seen_at") val lastSeenAt: Long = 0,
    val current: Boolean = false,
)

@Serializable
data class PairRequest(
    @SerialName("device_name") val deviceName: String,
    val platform: String,
)

@Serializable
data class Pairing(
    val code: String,
    val secret: String,
    @SerialName("approve_url") val approveUrl: String,
    @SerialName("expires_in") val expiresIn: Long,
    val interval: Long,
)

@Serializable
data class TokenRequest(val secret: String)

@Serializable
data class Granted(
    @SerialName("access_token") val accessToken: String,
    val device: Device,
    val user: User,
)

@Serializable
data class Library(
    val id: String,
    val name: String,
    val kind: String,
    @SerialName("item_count") val itemCount: Long = 0,
)

@Serializable
data class Images(
    val poster: String? = null,
    val backdrop: String? = null,
    val thumb: String? = null,
    val logo: String? = null,
)

@Serializable
data class UserData(
    @SerialName("position_ms") val positionMs: Long = 0,
    val played: Boolean = false,
    val favorite: Boolean = false,
)

@Serializable
data class Item(
    val id: Long,
    @SerialName("library_id") val libraryId: String = "",
    val kind: String,
    val title: String,
    val year: Int? = null,
    @SerialName("parent_id") val parentId: Long? = null,
    @SerialName("series_id") val seriesId: Long? = null,
    @SerialName("series_title") val seriesTitle: String? = null,
    @SerialName("season_number") val seasonNumber: Int? = null,
    @SerialName("episode_number") val episodeNumber: Int? = null,
    @SerialName("extra_type") val extraType: String? = null,
    @SerialName("runtime_ms") val runtimeMs: Long? = null,
    val rating: Double? = null,
    @SerialName("official_rating") val officialRating: String? = null,
    val genres: List<String> = emptyList(),
    val images: Images = Images(),
    val user: UserData? = null,
    @SerialName("child_count") val childCount: Int? = null,
    @SerialName("unplayed_count") val unplayedCount: Int? = null,
    // Details only.
    val overview: String? = null,
    val tagline: String? = null,
    val people: List<Person> = emptyList(),
    val media: Media? = null,
    val trickplay: Trickplay? = null,
) {
    val playable: Boolean get() = kind == "movie" || kind == "episode" || kind == "extra"

    /** "S1 · E3" for episodes, the year otherwise. */
    val subtitle: String?
        get() = when {
            kind == "episode" && seasonNumber != null && episodeNumber != null ->
                "S$seasonNumber · E$episodeNumber"
            kind == "season" -> null
            else -> year?.toString()
        }
}

@Serializable
data class Person(
    val name: String,
    val role: String,
    val character: String? = null,
)

@Serializable
data class Media(
    val container: String = "",
    @SerialName("duration_ms") val durationMs: Long? = null,
    val video: VideoStream? = null,
    val audio: List<AudioStream> = emptyList(),
    val subtitles: List<SubtitleStream> = emptyList(),
    val chapters: List<Chapter> = emptyList(),
)

@Serializable
data class VideoStream(
    val codec: String,
    val width: Int = 0,
    val height: Int = 0,
    val hdr: String? = null,
)

@Serializable
data class AudioStream(
    val index: Int,
    val codec: String,
    val channels: Int? = null,
    val language: String? = null,
    val title: String? = null,
    val default: Boolean = false,
)

@Serializable
data class SubtitleStream(
    val index: Int,
    val codec: String,
    val language: String? = null,
    val title: String? = null,
    val default: Boolean = false,
    val forced: Boolean = false,
    val external: Boolean = false,
    val text: Boolean = true,
)

@Serializable
data class Chapter(
    @SerialName("start_ms") val startMs: Long,
    val title: String? = null,
)

@Serializable
data class Trickplay(
    @SerialName("interval_ms") val intervalMs: Long,
    val width: Int,
    val height: Int,
    val columns: Int,
    val rows: Int,
    val thumbnails: Int,
    val sheets: List<String>,
)

@Serializable
data class LatestRow(val library: Library, val items: List<Item>)

@Serializable
data class Home(
    @SerialName("continue_watching") val continueWatching: List<Item> = emptyList(),
    @SerialName("next_up") val nextUp: List<Item> = emptyList(),
    val latest: List<LatestRow> = emptyList(),
)

@Serializable
data class ItemPage(val items: List<Item>, val total: Long)

@Serializable
data class Capabilities(
    val containers: List<String>,
    @SerialName("video_codecs") val videoCodecs: List<String>,
    @SerialName("audio_codecs") val audioCodecs: List<String>,
    val hdr: List<String>,
    @SerialName("selects_tracks") val selectsTracks: Boolean = true,
)

@Serializable
data class PlaybackRequest(
    @SerialName("start_ms") val startMs: Long,
    @SerialName("audio_index") val audioIndex: Int? = null,
    @SerialName("burn_subtitle") val burnSubtitle: Int? = null,
    @SerialName("force_transcode") val forceTranscode: Boolean = false,
    val capabilities: Capabilities,
)

@Serializable
data class Playback(
    val method: String,
    val url: String,
    @SerialName("session_id") val sessionId: String? = null,
    @SerialName("start_ms") val startMs: Long = 0,
    @SerialName("duration_ms") val durationMs: Long? = null,
    @SerialName("tone_mapping") val toneMapping: Boolean = false,
    @SerialName("burned_subtitle") val burnedSubtitle: Int? = null,
    val reasons: List<String> = emptyList(),
) {
    val hls: Boolean get() = method != "direct"
}

@Serializable
data class Progress(@SerialName("position_ms") val positionMs: Long)
