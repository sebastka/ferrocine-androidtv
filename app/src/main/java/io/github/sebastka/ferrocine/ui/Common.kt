package io.github.sebastka.ferrocine.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.darkColorScheme
import coil3.compose.AsyncImage
import io.github.sebastka.ferrocine.FerrocineApp
import io.github.sebastka.ferrocine.api.Api
import io.github.sebastka.ferrocine.api.ApiException
import io.github.sebastka.ferrocine.api.Item

val Accent = Color(0xFFE0703F)

@Composable
fun FerrocineTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            onPrimary = Color(0xFF1A0C05),
            background = Color(0xFF121212),
            surface = Color(0xFF1C1C1C),
            onSurface = Color(0xFFEDEDED),
            surfaceVariant = Color(0xFF2A2A2A),
            border = Accent,
        ),
        content = content,
    )
}

@Composable
fun app(): FerrocineApp = LocalContext.current.applicationContext as FerrocineApp

/** Loading, failed (with a message), or loaded. */
sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Failed(val message: String) : Load<Nothing>
    data class Loaded<T>(val value: T) : Load<T>
}

fun errorMessage(e: Throwable): String = when (e) {
    is ApiException -> when (e.status) {
        404 -> "Not found."
        else -> e.error ?: "The server answered ${e.status}."
    }
    is java.net.UnknownHostException -> "The server cannot be found."
    is java.net.ConnectException, is java.net.SocketTimeoutException -> "The server does not answer."
    is javax.net.ssl.SSLException -> "The server's certificate is not trusted."
    else -> e.message ?: e.javaClass.simpleName
}

/**
 * Load with the account's API whenever [keys] change, showing what was
 * loaded before meanwhile (no flash on a reload); a 401 signs the app out
 * (the device was signed out on the server).
 */
@Composable
fun <T> rememberLoad(vararg keys: Any?, block: suspend Api.() -> T): State<Load<T>> {
    val app = app()
    val state = remember { mutableStateOf<Load<T>>(Load.Loading) }
    LaunchedEffect(*keys) {
        state.value = try {
            Load.Loaded(app.api().block())
        } catch (e: ApiException) {
            if (e.status == 401) app.signOut()
            Load.Failed(errorMessage(e))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Load.Failed(errorMessage(e))
        }
    }
    return state
}

@Composable
fun <T> Loaded(load: Load<T>, content: @Composable (T) -> Unit) {
    when (load) {
        Load.Loading -> Centered { Text("Loading…", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)) }
        is Load.Failed -> Centered { Text(load.message, color = MaterialTheme.colorScheme.error) }
        is Load.Loaded -> content(load.value)
    }
}

@Composable
fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

/** Card shapes: posters for movies and series, wide thumbs for episodes. */
fun Item.wide(): Boolean = kind == "episode" || kind == "extra"

/** An item as a card: its poster (or thumb), progress, title and subtitle. */
@Composable
fun ItemCard(
    item: Item,
    api: Api,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = if (item.wide()) 240.dp else 140.dp,
    showSeries: Boolean = true,
) {
    val image = if (item.wide()) item.images.thumb ?: item.images.backdrop else item.images.poster
    Column(modifier.width(width)) {
        Card(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().aspectRatio(if (item.wide()) 16f / 9f else 2f / 3f),
            colors = CardDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(Modifier.fillMaxSize()) {
                if (image != null) {
                    AsyncImage(
                        model = api.url(image),
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        item.title,
                        modifier = Modifier.align(Alignment.Center).padding(8.dp),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                val runtime = item.runtimeMs ?: 0
                val pos = item.user?.positionMs ?: 0
                if (runtime > 0 && pos > 0 && item.user?.played != true) {
                    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(Color(0x99000000))) {
                        Box(
                            Modifier.fillMaxWidth((pos.toFloat() / runtime).coerceIn(0f, 1f)).height(4.dp)
                                .background(Accent),
                        )
                    }
                }
                val unplayed = item.unplayedCount ?: 0
                if (unplayed > 0) {
                    Text(
                        "$unplayed",
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
                            .background(Accent, MaterialTheme.shapes.small).padding(horizontal = 6.dp),
                        color = Color(0xFF1A0C05),
                        style = MaterialTheme.typography.labelMedium,
                    )
                } else if (item.user?.played == true) {
                    Text(
                        "✓",
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
                            .background(Accent, MaterialTheme.shapes.small).padding(horizontal = 6.dp),
                        color = Color(0xFF1A0C05),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
        // Room for the focused card, which grows by a tenth.
        Spacer(Modifier.height(14.dp))
        val title = if (showSeries && item.kind == "episode" && item.seriesTitle != null) item.seriesTitle else item.title
        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
        val sub = if (showSeries && item.kind == "episode") listOfNotNull(item.subtitle, item.title).joinToString(" · ")
        else item.subtitle
        if (sub != null) {
            Text(
                sub,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
    }
}

/** A titled row of cards. */
@Composable
fun ItemRow(title: String, items: List<Item>, api: Api, onOpen: (Item) -> Unit) {
    if (items.isEmpty()) return
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 48.dp, bottom = 8.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(items, key = { it.id }) { item -> ItemCard(item, api, onClick = { onOpen(item) }) }
        }
    }
}

fun formatDuration(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

/** "1 h 52 min", "48 min". */
fun formatRuntime(ms: Long): String {
    val min = (ms + 30_000) / 60_000
    return if (min >= 60) "${min / 60} h ${min % 60} min" else "$min min"
}
