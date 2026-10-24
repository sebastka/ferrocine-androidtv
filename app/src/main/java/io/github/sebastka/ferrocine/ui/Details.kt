package io.github.sebastka.ferrocine.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.FilterChip
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import io.github.sebastka.ferrocine.api.Item
import kotlinx.coroutines.launch

/** An item's page: artwork, facts, actions, and its seasons, episodes or extras. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DetailsScreen(id: Long, onOpen: (Item) -> Unit, onPlay: (Item, Boolean) -> Unit) {
    val app = app()
    // Reloaded when coming back from the player (positions changed).
    var round by remember { mutableIntStateOf(0) }
    val load by rememberLoad(id, round) {
        val item = item(id)
        item to (if (item.kind != "episode") children(id) else emptyList())
    }
    Loaded(load) { (item, children) ->
        val api = app.api()
        var favorite by remember(item) { mutableStateOf(item.user?.favorite == true) }
        var played by remember(item) { mutableStateOf(item.user?.played == true) }
        val scope = rememberCoroutineScope()
        val first = remember { FocusRequester() }
        Box(Modifier.fillMaxSize()) {
            val backdrop = item.images.backdrop ?: item.images.thumb
            if (backdrop != null) {
                AsyncImage(
                    model = api.url(backdrop),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(
                Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(0f to Color(0xF2121212), 0.65f to Color(0xB3121212), 1f to Color(0x40121212)),
                ),
            )
            // Scroll only as far as needed to show what has focus: TV's default
            // brings it to a third of the screen, which pushes the title out.
            CompositionLocalProvider(LocalBringIntoViewSpec provides MinimalScroll) {
            LazyColumn(
                contentPadding = PaddingValues(start = 64.dp, end = 64.dp, top = 48.dp, bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    if (item.kind == "episode" && item.seriesTitle != null) {
                        Text(item.seriesTitle, style = MaterialTheme.typography.titleMedium, color = Accent)
                    }
                    if (item.images.logo != null && item.kind != "episode") {
                        AsyncImage(
                            model = api.url(item.images.logo),
                            contentDescription = item.title,
                            modifier = Modifier.height(96.dp).width(420.dp),
                            alignment = Alignment.CenterStart,
                        )
                    } else {
                        Text(item.title, style = MaterialTheme.typography.displaySmall)
                    }
                }
                item { Facts(item) }
                item.tagline?.let { t -> item { Text(t, style = MaterialTheme.typography.titleMedium) } }
                item.overview?.let { o ->
                    item {
                        Text(
                            o,
                            maxLines = 5,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(0.6f),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        val position = item.user?.positionMs ?: 0
                        if (item.playable) {
                            if (position > 0 && !played) {
                                Button(onClick = { onPlay(item, true) }, modifier = Modifier.focusRequester(first)) {
                                    Icon(Icons.Default.PlayArrow, null)
                                    Text("  Resume from ${formatDuration(position)}")
                                }
                                OutlinedButton(onClick = { onPlay(item, false) }) {
                                    Icon(Icons.Default.Replay, null)
                                    Text("  From the start")
                                }
                            } else {
                                Button(onClick = { onPlay(item, false) }, modifier = Modifier.focusRequester(first)) {
                                    Icon(Icons.Default.PlayArrow, null)
                                    Text("  Play")
                                }
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                played = !played
                                scope.launch { runCatching { app.api().setPlayed(item.id, played) }; round++ }
                            },
                            modifier = if (item.playable) Modifier else Modifier.focusRequester(first),
                        ) {
                            Icon(Icons.Default.Check, null, tint = if (played) Accent else androidx.tv.material3.LocalContentColor.current)
                            Text(if (played) "  Played" else "  Mark played")
                        }
                        OutlinedButton(
                            onClick = {
                                favorite = !favorite
                                scope.launch { runCatching { app.api().setFavorite(item.id, favorite) } }
                            },
                        ) {
                            Icon(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null,
                                tint = if (favorite) Accent else androidx.tv.material3.LocalContentColor.current)
                        }
                    }
                    LaunchedEffect(item.id) { runCatching { first.requestFocus() } }
                }
                when (item.kind) {
                    "series" -> item { Seasons(item, children, onOpen) }
                    "season" -> item { EpisodeRow("Episodes", children, onOpen) }
                    "movie" -> if (children.isNotEmpty()) item { EpisodeRow("Extras", children, onOpen) }
                }
                val cast = item.people.filter { it.role == "actor" }.take(12)
                if (cast.isNotEmpty()) {
                    item {
                        Text(
                            "Cast: " + cast.joinToString(", ") { p -> p.character?.let { "${p.name} ($it)" } ?: p.name },
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(0.7f),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            }
        }
    }
    // Coming back from the player: reload positions.
    var paused by remember { mutableStateOf(false) }
    androidx.lifecycle.compose.LifecycleResumeEffect(id) {
        if (paused) round++
        onPauseOrDispose { paused = true }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private val MinimalScroll = object : BringIntoViewSpec {}

@Composable
private fun Facts(item: Item) {
    val parts = buildList {
        item.subtitle?.let { add(it) }
        if (item.kind == "episode" && item.year != null) add(item.year.toString())
        item.runtimeMs?.let { add(formatRuntime(it)) }
        item.officialRating?.let { add(it) }
        item.rating?.let { add("★ %.1f".format(it)) }
        if (item.genres.isNotEmpty()) add(item.genres.take(3).joinToString(", "))
        item.media?.video?.let { v ->
            add(
                listOfNotNull(
                    when {
                        v.height >= 2000 -> "4K"
                        v.height >= 1000 -> "HD"
                        else -> null
                    },
                    when (v.hdr) {
                        "dolby_vision" -> "Dolby Vision"
                        "hdr10" -> "HDR10"
                        "hlg" -> "HLG"
                        else -> null
                    },
                ).joinToString(" "),
            )
        }
    }.filter { it.isNotBlank() }
    Text(parts.joinToString("  ·  "), style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
}

@Composable
private fun Seasons(series: Item, seasons: List<Item>, onOpen: (Item) -> Unit) {
    if (seasons.isEmpty()) return
    var selected by rememberSaveable(series.id) {
        // The first season with something unplayed, else the first.
        mutableStateOf((seasons.firstOrNull { (it.unplayedCount ?: 0) > 0 && it.seasonNumber != 0 } ?: seasons.first()).id)
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(seasons, key = { it.id }) { s ->
                FilterChip(selected = s.id == selected, onClick = { selected = s.id }) { Text(s.title) }
            }
        }
        val episodes by rememberLoad(selected) { children(selected) }
        when (val e = episodes) {
            is Load.Loaded -> EpisodeRow(null, e.value, onOpen)
            is Load.Failed -> Text(e.message, color = MaterialTheme.colorScheme.error)
            Load.Loading -> Text("Loading…")
        }
    }
}

@Composable
private fun EpisodeRow(title: String?, episodes: List<Item>, onOpen: (Item) -> Unit) {
    val api = app().api()
    Column {
        if (title != null) Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
            items(episodes, key = { it.id }) { ep -> ItemCard(ep, api, onClick = { onOpen(ep) }, showSeries = false) }
        }
    }
}
