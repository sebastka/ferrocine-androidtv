package io.github.sebastka.ferrocine.player

import android.net.Uri
import android.util.Log
import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Size as CoilSize
import io.github.sebastka.ferrocine.api.Api
import io.github.sebastka.ferrocine.api.AudioStream
import io.github.sebastka.ferrocine.api.Item
import io.github.sebastka.ferrocine.api.Playback
import io.github.sebastka.ferrocine.api.PlaybackRequest
import io.github.sebastka.ferrocine.api.SubtitleStream
import io.github.sebastka.ferrocine.ui.Accent
import io.github.sebastka.ferrocine.ui.Loaded
import io.github.sebastka.ferrocine.ui.app
import io.github.sebastka.ferrocine.ui.errorMessage
import io.github.sebastka.ferrocine.ui.formatDuration
import io.github.sebastka.ferrocine.ui.rememberLoad
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The id of the subtitle the server sends as WebVTT, to find it among the player's tracks. */
private const val SIDELOADED = "ferrocine-subtitle"
private const val TAG = "FerrocinePlayer"

@Composable
fun PlayerScreen(id: Long, resume: Boolean, onExit: () -> Unit, onNext: (Item) -> Unit) {
    val load by rememberLoad(id) { item(id) }
    Loaded(load) { item -> Player(item, resume, onExit, onNext) }
}

@OptIn(UnstableApi::class)
@Composable
private fun Player(item: Item, resume: Boolean, onExit: () -> Unit, onNext: (Item) -> Unit) {
    val app = app()
    val api = remember { app.api() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val media = item.media
    val caps = remember { DeviceCapabilities.probe(context, app::passthrough) }
    val exo = remember {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(OkHttpDataSource.Factory(app.http)))
            .build()
    }

    var playback by remember { mutableStateOf<Playback?>(null) }
    var audio by remember { mutableStateOf(media?.defaultAudio()) }
    var subtitle by remember { mutableStateOf<SubtitleStream?>(null) }
    // Set when the player cannot show a bitmap subtitle itself, or decode the file.
    var forceBurn by remember { mutableStateOf(false) }
    var forceTranscode by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var position by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(false) }
    var controls by remember { mutableStateOf(true) }
    var menu by remember { mutableStateOf(false) }
    var scrub by remember { mutableStateOf<Long?>(null) }
    var lastKey by remember { mutableLongStateOf(0L) }
    val duration = media?.durationMs ?: item.runtimeMs ?: 0L

    fun offset(): Long = playback?.let { if (it.hls) it.startMs else 0 } ?: 0
    fun itemPosition(): Long = offset() + exo.currentPosition.coerceAtLeast(0)

    fun report(at: Long) {
        app.scope.launch { runCatching { api.progress(item.id, at) } }
    }

    fun request(at: Long, burn: Int?) = PlaybackRequest(
        startMs = at,
        audioIndex = audio?.index,
        burnSubtitle = burn,
        forceTranscode = forceTranscode,
        capabilities = caps,
    )

    suspend fun start(at: Long) {
        error = null
        val old = playback
        val sub = subtitle
        val bitmap = sub != null && !sub.text
        val pb = try {
            // Already streaming through the server: it will transcode anyway, so burn at once.
            val burnNow = bitmap && (forceBurn || old?.hls == true)
            var pb = api.playback(item.id, request(at, if (burnNow) sub.index else null))
            // Transcoding anyway: the server draws a bitmap subtitle in.
            if (bitmap && pb.hls && pb.burnedSubtitle == null) {
                pb.sessionId?.let { api.stopHls(it) }
                pb = api.playback(item.id, request(at, sub.index))
            }
            pb
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            error = errorMessage(e)
            return
        }
        old?.sessionId?.let { sid -> app.scope.launch { api.stopHls(sid) } }
        playback = pb
        Log.i(TAG, "playback ${pb.method} at $at: ${pb.reasons.joinToString("; ")}")
        val builder = MediaItem.Builder().setUri(api.url(pb.url))
        if (pb.hls) {
            builder.setMimeType(MimeTypes.APPLICATION_M3U8)
                // The playlist grows as ffmpeg works: never speed up to "catch up" with it.
                .setLiveConfiguration(
                    MediaItem.LiveConfiguration.Builder().setMinPlaybackSpeed(1f).setMaxPlaybackSpeed(1f).build(),
                )
        }
        if (sub != null && subtitleWay(sub, pb.hls) == SubtitleWay.Sideloaded) {
            val url = api.subtitleUrl(item.id, sub.index, if (pb.hls) pb.startMs else 0)
            builder.setSubtitleConfigurations(
                listOf(
                    MediaItem.SubtitleConfiguration.Builder(Uri.parse(url))
                        .setMimeType(MimeTypes.TEXT_VTT)
                        .setLanguage(sub.language)
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                        .setId(SIDELOADED)
                        .build(),
                ),
            )
        }
        exo.setMediaItem(builder.build(), if (pb.hls) 0 else at)
        exo.prepare()
        exo.play()
    }

    fun restart(at: Long = itemPosition()) {
        scope.launch { start(at) }
    }

    /** Select the chosen audio and subtitle among the player's tracks. */
    fun applyTracks(tracks: Tracks) {
        val pb = playback ?: return
        val m = media ?: return
        val params = exo.trackSelectionParameters.buildUpon()
        val a = audio
        if (!pb.hls && a != null) {
            tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }.getOrNull(m.audioOrdinal(a))?.let {
                params.setOverrideForType(TrackSelectionOverride(it.mediaTrackGroup, 0))
            }
        }
        val texts = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
        fun sideloaded(g: Tracks.Group) = g.mediaTrackGroup.getFormat(0).id?.endsWith(SIDELOADED) == true
        val sub = subtitle
        val target = when {
            sub == null -> null
            else -> when (subtitleWay(sub, pb.hls)) {
                SubtitleWay.Sideloaded -> texts.firstOrNull(::sideloaded)
                SubtitleWay.Embedded -> texts.filterNot(::sideloaded)
                    .getOrNull(m.embeddedSubtitles().indexOfFirst { it.index == sub.index })
                SubtitleWay.Burned -> null
            }
        }
        if (sub != null && !sub.text && !pb.hls && target?.isSupported != true) {
            // The player cannot draw this bitmap subtitle: the server will.
            forceBurn = true
            restart()
            return
        }
        if (target == null) {
            params.clearOverridesOfType(C.TRACK_TYPE_TEXT).setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        } else {
            params.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setOverrideForType(TrackSelectionOverride(target.mediaTrackGroup, 0))
        }
        exo.trackSelectionParameters = params.build()
        Log.i(TAG, "tracks: audio ${a?.index}, subtitle ${sub?.index} → ${target?.mediaTrackGroup?.getFormat(0)?.id}")
    }

    fun next() {
        val parent = item.parentId
        if (item.kind != "episode" || parent == null) {
            onExit()
            return
        }
        scope.launch {
            val n = runCatching { nextEpisode(item, api.children(parent)) }.getOrNull()
            if (n != null) onNext(n) else onExit()
        }
    }

    DisposableEffect(exo) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) = applyTracks(tracks)

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
                if (!isPlaying && exo.playbackState == Player.STATE_READY) report(itemPosition())
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    report(duration)
                    next()
                }
            }

            override fun onPlayerError(e: PlaybackException) {
                val pb = playback
                val http = e.cause as? HttpDataSource.InvalidResponseCodeException
                when {
                    // A segment the server dropped (far behind) or a session it stopped: start again here.
                    pb != null && pb.hls && http != null && http.responseCode == 404 -> restart()
                    // The file does not decode here after all: let the server transcode it.
                    pb != null && !pb.hls && !forceTranscode -> {
                        forceTranscode = true
                        restart()
                    }
                    else -> error = e.message ?: "Playback failed (${e.errorCodeName})."
                }
            }
        }
        exo.addListener(listener)
        onDispose {
            exo.removeListener(listener)
            val at = itemPosition()
            if (exo.playbackState != Player.STATE_ENDED && playback != null) report(at)
            playback?.sessionId?.let { sid -> app.scope.launch { api.stopHls(sid) } }
            exo.release()
        }
    }

    LaunchedEffect(Unit) {
        start(if (resume) item.user?.positionMs ?: 0 else 0)
    }
    // The clock, and a progress report every 10 s of playback.
    LaunchedEffect(Unit) {
        var sinceReport = 0L
        while (true) {
            delay(500)
            position = itemPosition()
            if (exo.isPlaying) {
                sinceReport += 500
                if (sinceReport >= 10_000) {
                    sinceReport = 0
                    report(position)
                }
            }
        }
    }
    // Controls hide after 5 s while playing.
    LaunchedEffect(controls, lastKey, playing, menu) {
        if (controls && playing && !menu && scrub == null) {
            delay(5_000)
            controls = false
        }
    }

    fun seekTo(at: Long) {
        val target = at.coerceIn(0, (duration - 1_000).coerceAtLeast(0))
        val pb = playback ?: return
        val window = Window(offset(), exo.duration.takeIf { it != C.TIME_UNSET } ?: 0, pb.hls)
        val p = window.streamPosition(target)
        if (p != null) exo.seekTo(p) else restart(target)
        position = target
    }
    // Scrubbing settles 1.5 s after the last press.
    LaunchedEffect(scrub, lastKey) {
        val s = scrub ?: return@LaunchedEffect
        delay(1_500)
        seekTo(s)
        scrub = null
    }

    BackHandler {
        when {
            menu -> menu = false
            scrub != null -> scrub = null
            controls && playing -> controls = false
            else -> onExit()
        }
    }

    val root = remember { FocusRequester() }
    LaunchedEffect(menu) { if (!menu) runCatching { root.requestFocus() } }

    fun onKey(e: AndroidKeyEvent): Boolean {
        lastKey = System.nanoTime()
        val ok = e.keyCode == AndroidKeyEvent.KEYCODE_DPAD_CENTER || e.keyCode == AndroidKeyEvent.KEYCODE_ENTER
        if (error != null && ok) {
            // OK on the error box tries again, where playback stood.
            restart(position)
            return true
        }
        when (e.keyCode) {
            AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_ENTER, AndroidKeyEvent.KEYCODE_NUMPAD_ENTER -> {
                val s = scrub
                when {
                    s != null -> {
                        seekTo(s)
                        scrub = null
                    }
                    !controls -> controls = true
                    else -> if (exo.isPlaying) exo.pause() else exo.play()
                }
            }
            AndroidKeyEvent.KEYCODE_DPAD_LEFT, AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                val step = scrubStep(e.repeatCount)
                val sign = if (e.keyCode == AndroidKeyEvent.KEYCODE_DPAD_LEFT) -1 else 1
                scrub = ((scrub ?: position) + sign * step).coerceIn(0, duration.coerceAtLeast(0))
                controls = true
            }
            AndroidKeyEvent.KEYCODE_DPAD_DOWN, AndroidKeyEvent.KEYCODE_MENU, AndroidKeyEvent.KEYCODE_CAPTIONS -> {
                if (media != null) menu = true
            }
            AndroidKeyEvent.KEYCODE_DPAD_UP -> controls = true
            AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, AndroidKeyEvent.KEYCODE_SPACE -> {
                if (exo.isPlaying) exo.pause() else exo.play()
                controls = true
            }
            AndroidKeyEvent.KEYCODE_MEDIA_PLAY -> exo.play()
            AndroidKeyEvent.KEYCODE_MEDIA_PAUSE -> {
                exo.pause()
                controls = true
            }
            AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> seekTo(position + 30_000)
            AndroidKeyEvent.KEYCODE_MEDIA_REWIND -> seekTo(position - 10_000)
            AndroidKeyEvent.KEYCODE_MEDIA_STOP -> onExit()
            else -> return false
        }
        return true
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .focusRequester(root)
            .onKeyEvent { if (it.type == KeyEventType.KeyDown && !menu) onKey(it.nativeKeyEvent) else false }
            .focusable(),
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    player = exo
                    keepScreenOn = true
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        if (controls || scrub != null) {
            Controls(item, api, position, scrub, duration, playing, playback)
        }
        error?.let { msg ->
            Column(
                Modifier.align(Alignment.Center).background(Color(0xE0121212), MaterialTheme.shapes.medium).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Cannot play ${item.title}", style = MaterialTheme.typography.titleLarge)
                Text(msg, color = MaterialTheme.colorScheme.error)
                Text("Press back to leave, OK to try again.", style = MaterialTheme.typography.bodySmall)
            }
        }
        if (menu && media != null) {
            TrackMenu(
                audio = media.audio,
                subtitles = media.selectableSubtitles(),
                chosenAudio = audio,
                chosenSubtitle = subtitle,
                onAudio = { a ->
                    menu = false
                    if (a.index == audio?.index) return@TrackMenu
                    audio = a
                    // The player switches in the file only to audio this TV plays;
                    // for the rest, the server converts it.
                    val playable = caps.audioCodecs.any { it.equals(a.codec, ignoreCase = true) }
                    if (playback?.hls == false && playable) applyTracks(exo.currentTracks) else restart()
                },
                onSubtitle = { s ->
                    menu = false
                    val pb = playback
                    val was = subtitle
                    if (s?.index == was?.index) return@TrackMenu
                    subtitle = s
                    forceBurn = false
                    val inPlace = pb != null && pb.burnedSubtitle == null &&
                        (s == null || (!pb.hls && subtitleWay(s, false) == SubtitleWay.Embedded))
                    if (inPlace) applyTracks(exo.currentTracks) else restart()
                },
            )
        }
    }
}

@Composable
private fun Controls(
    item: Item,
    api: Api,
    position: Long,
    scrub: Long?,
    duration: Long,
    playing: Boolean,
    playback: Playback?,
) {
    val shown = scrub ?: position
    val chapters = item.media?.chapters.orEmpty()
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0f to Color(0x99000000), 0.25f to Color.Transparent, 0.6f to Color.Transparent, 1f to Color(0xCC000000)),
            ),
        )
        Column(Modifier.align(Alignment.TopStart).padding(48.dp)) {
            if (item.kind == "episode") {
                Text(item.seriesTitle.orEmpty(), style = MaterialTheme.typography.titleMedium, color = Accent)
                Text(listOfNotNull(item.subtitle, item.title).joinToString(" · "), style = MaterialTheme.typography.headlineSmall)
            } else {
                Text(item.title, style = MaterialTheme.typography.headlineSmall)
            }
            val notes = buildList {
                when (playback?.method) {
                    "remux" -> add("Remuxed")
                    "transcode" -> add("Transcoded")
                }
                if (playback?.toneMapping == true) add("HDR tone-mapped")
                if (playback?.burnedSubtitle != null) add("Subtitles burned in")
            }
            if (notes.isNotEmpty()) {
                Text(notes.joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f))
            }
        }
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 48.dp, vertical = 32.dp)) {
            BoxWithConstraints(Modifier.fillMaxWidth().height(if (scrub != null) 200.dp else 24.dp)) {
                val frac = if (duration > 0) (shown.toFloat() / duration).coerceIn(0f, 1f) else 0f
                if (scrub != null) {
                    val previewWidth = 240.dp
                    val x = (maxWidth * frac - previewWidth / 2).coerceIn(0.dp, maxWidth - previewWidth)
                    Column(Modifier.offset(x = x).width(previewWidth), horizontalAlignment = Alignment.CenterHorizontally) {
                        item.trickplay?.let { Preview(it, scrub, api) }
                        chapters.at(scrub)?.title?.let {
                            Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                        }
                        Text(formatDuration(scrub), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            SeekBar(position, scrub, duration, chapters.map { it.startMs })
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                // What OK does: pause while playing, play while paused.
                androidx.tv.material3.Icon(
                    if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (playing) "Pause" else "Play",
                )
                Spacer(Modifier.width(16.dp))
                Text("${formatDuration(position)} / ${formatDuration(duration)}", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                chapters.at(position)?.title?.takeIf { scrub == null }?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.7f))
                    Spacer(Modifier.width(24.dp))
                }
                Text("↓ Audio & subtitles", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
            }
        }
    }
}

@Composable
private fun SeekBar(position: Long, scrub: Long?, duration: Long, chapterStarts: List<Long>) {
    Canvas(Modifier.fillMaxWidth().height(14.dp)) {
        val h = 6.dp.toPx()
        val y = (size.height - h) / 2
        fun x(ms: Long) = if (duration > 0) size.width * (ms.toFloat() / duration).coerceIn(0f, 1f) else 0f
        drawRoundRect(Color.White.copy(alpha = 0.25f), Offset(0f, y), Size(size.width, h))
        drawRoundRect(Accent, Offset(0f, y), Size(x(position), h))
        for (c in chapterStarts) {
            if (c > 0) drawRect(Color.Black.copy(alpha = 0.7f), Offset(x(c) - 1.dp.toPx(), y), Size(2.dp.toPx(), h))
        }
        val head = x(scrub ?: position)
        drawCircle(Color.White, radius = if (scrub != null) 9.dp.toPx() else 6.dp.toPx(), center = Offset(head, size.height / 2))
    }
}

/** One frame of the seek bar previews: its sheet, shifted and clipped to the frame. */
@Composable
private fun Preview(t: io.github.sebastka.ferrocine.api.Trickplay, at: Long, api: Api) {
    val tile = t.tileAt(at) ?: return
    val w = 240.dp
    val h = w * t.height / t.width
    val context = LocalContext.current
    Box(Modifier.size(w, h).clipToBounds().background(Color.Black)) {
        AsyncImage(
            model = ImageRequest.Builder(context).data(api.url(t.sheets[tile.sheet])).size(CoilSize.ORIGINAL).build(),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            alignment = Alignment.TopStart,
            // The whole sheet, from the frame's top left corner (not centred).
            modifier = Modifier
                .wrapContentSize(Alignment.TopStart, unbounded = true)
                .requiredSize(w * t.columns, h * t.rows)
                .offset(x = -w * tile.column, y = -h * tile.row),
        )
    }
}

@Composable
private fun TrackMenu(
    audio: List<AudioStream>,
    subtitles: List<SubtitleStream>,
    chosenAudio: AudioStream?,
    chosenSubtitle: SubtitleStream?,
    onAudio: (AudioStream) -> Unit,
    onSubtitle: (SubtitleStream?) -> Unit,
) {
    val first = remember { FocusRequester() }
    Box(Modifier.fillMaxSize()) {
        Row(
            Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(720.dp)
                .background(Color(0xF0181818)).padding(32.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text("Audio", style = MaterialTheme.typography.titleMedium)
                LazyColumn {
                    items(audio, key = { it.index }) { a ->
                        val selected = a.index == chosenAudio?.index
                        ListItem(
                            selected = selected,
                            onClick = { onAudio(a) },
                            headlineContent = { Text(a.label(), maxLines = 2) },
                            modifier = if (selected) Modifier.focusRequester(first) else Modifier,
                        )
                    }
                }
            }
            Column(Modifier.weight(1f)) {
                Text("Subtitles", style = MaterialTheme.typography.titleMedium)
                LazyColumn {
                    item {
                        ListItem(
                            selected = chosenSubtitle == null,
                            onClick = { onSubtitle(null) },
                            headlineContent = { Text("Off") },
                            modifier = if (audio.isEmpty()) Modifier.focusRequester(first) else Modifier,
                        )
                    }
                    items(subtitles, key = { it.index }) { s ->
                        ListItem(
                            selected = s.index == chosenSubtitle?.index,
                            onClick = { onSubtitle(s) },
                            headlineContent = { Text(s.label(), maxLines = 2) },
                        )
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}
