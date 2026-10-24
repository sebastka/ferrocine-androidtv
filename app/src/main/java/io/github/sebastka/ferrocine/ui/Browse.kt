package io.github.sebastka.ferrocine.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.FilterChip
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.NavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.Text
import io.github.sebastka.ferrocine.BuildConfig
import io.github.sebastka.ferrocine.api.Item
import io.github.sebastka.ferrocine.api.Library
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private sealed interface Section {
    data object Home : Section
    data object Search : Section
    data object Settings : Section
    data class Lib(val library: Library) : Section
}

/** The signed-in app: a side menu (home, search, libraries, settings) and its page. */
@Composable
fun Browse(onOpen: (Item) -> Unit) {
    val libraries by rememberLoad { libraries() }
    var selectedKey by rememberSaveable { mutableStateOf("home") }
    val libs = (libraries as? Load.Loaded)?.value.orEmpty()
    val sections = listOf<Section>(Section.Home, Section.Search) + libs.map { Section.Lib(it) } + Section.Settings
    fun key(s: Section) = when (s) {
        Section.Home -> "home"
        Section.Search -> "search"
        Section.Settings -> "settings"
        is Section.Lib -> "lib:${s.library.id}"
    }
    val selected = sections.firstOrNull { key(it) == selectedKey } ?: Section.Home

    NavigationDrawer(
        drawerContent = {
            Column(
                Modifier.fillMaxHeight().background(MaterialTheme.colorScheme.surface).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp, androidx.compose.ui.Alignment.CenterVertically),
            ) {
                for (s in sections) {
                    val (label, icon) = when (s) {
                        Section.Home -> "Home" to Icons.Default.Home
                        Section.Search -> "Search" to Icons.Default.Search
                        Section.Settings -> "Settings" to Icons.Default.Settings
                        is Section.Lib -> s.library.name to (if (s.library.kind == "shows") Icons.Default.Tv else Icons.Default.Movie)
                    }
                    NavigationDrawerItem(
                        selected = s == selected,
                        onClick = { selectedKey = key(s) },
                        leadingContent = { Icon(icon, contentDescription = null) },
                    ) { Text(label) }
                }
            }
        },
    ) {
        when (val s = selected) {
            Section.Home -> HomePage(onOpen)
            Section.Search -> SearchPage(onOpen)
            Section.Settings -> SettingsPage()
            is Section.Lib -> LibraryPage(s.library, onOpen)
        }
    }
}

@Composable
private fun HomePage(onOpen: (Item) -> Unit) {
    val app = app()
    val home by rememberLoad { home() }
    Loaded(home) { h ->
        val api = app.api()
        LazyColumn(
            contentPadding = PaddingValues(vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item { ItemRow("Continue watching", h.continueWatching, api, onOpen) }
            item { ItemRow("Next up", h.nextUp, api, onOpen) }
            items(h.latest, key = { it.library.id }) { row -> ItemRow("Latest in ${row.library.name}", row.items, api, onOpen) }
            if (h.continueWatching.isEmpty() && h.nextUp.isEmpty() && h.latest.all { it.items.isEmpty() }) {
                item { Text("Nothing here yet: the libraries may still be scanning.", Modifier.padding(48.dp)) }
            }
        }
    }
}

private val SORTS = listOf("title" to "Title", "added" to "Recently added", "year" to "Year", "rating" to "Rating")

@Composable
private fun LibraryPage(library: Library, onOpen: (Item) -> Unit) {
    val app = app()
    var sort by rememberSaveable(library.id) { mutableStateOf("title") }
    val items = remember(library.id, sort) { mutableStateListOf<Item>() }
    var total by remember(library.id, sort) { mutableStateOf<Long?>(null) }
    var error by remember(library.id, sort) { mutableStateOf<String?>(null) }
    var loading by remember(library.id, sort) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun more() {
        if (loading || (total != null && items.size >= total!!)) return
        loading = true
        scope.launch {
            try {
                val page = app.api().libraryItems(library.id, sort, items.size, 100)
                items.addAll(page.items)
                total = page.total
            } catch (e: Exception) {
                error = errorMessage(e)
            }
            loading = false
        }
    }
    LaunchedEffect(library.id, sort) { more() }

    Column(Modifier.fillMaxSize().padding(top = 32.dp)) {
        Row(
            Modifier.padding(horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Text(library.name, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.width(12.dp))
            for ((value, label) in SORTS) {
                FilterChip(selected = sort == value, onClick = { sort = value }) { Text(label) }
            }
            total?.let { Text("$it", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)) }
        }
        if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(48.dp))
        val api = app.api()
        LazyVerticalGrid(
            columns = GridCells.Adaptive(140.dp),
            contentPadding = PaddingValues(48.dp, 24.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            itemsIndexed(items, key = { _, it -> it.id }) { i, item ->
                if (i >= items.size - 20) LaunchedEffect(items.size) { more() }
                ItemCard(item, api, onClick = { onOpen(item) }, width = 140.dp)
            }
        }
    }
}

@Composable
private fun SearchPage(onOpen: (Item) -> Unit) {
    val app = app()
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<Load<List<Item>>?>(null) }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(query) {
        if (query.trim().length < 2) {
            results = null
            return@LaunchedEffect
        }
        delay(400)
        results = Load.Loading
        results = try {
            Load.Loaded(app.api().search(query.trim()))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Load.Failed(errorMessage(e))
        }
    }
    Column(Modifier.fillMaxSize().padding(top = 32.dp)) {
        BasicTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            textStyle = MaterialTheme.typography.titleLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(Accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.padding(horizontal = 48.dp).width(640.dp)
                .onFocusChanged { focused = it.isFocused }
                .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)
                .border(2.dp, if (focused) Accent else Color.Transparent, MaterialTheme.shapes.medium)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            decorationBox = { inner ->
                if (query.isEmpty()) {
                    Text("Titles, actors, directors…", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        style = MaterialTheme.typography.titleLarge)
                }
                inner()
            },
        )
        val r = results
        if (r != null) {
            Loaded(r) { list ->
                if (list.isEmpty()) {
                    Text("No results.", Modifier.padding(48.dp))
                } else {
                    val api = app.api()
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(140.dp),
                        contentPadding = PaddingValues(48.dp, 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        itemsIndexed(list, key = { _, it -> it.id }) { _, item ->
                            ItemCard(item, api, onClick = { onOpen(item) }, width = 140.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsPage() {
    val app = app()
    val account = app.account.value ?: return
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    Column(Modifier.padding(48.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)
        Text("Signed in as ${account.userName} on ${account.server.toString().trimEnd('/')}")
        Text(
            "This TV is listed among your devices in the web UI (profile), where it can be signed out too.",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        Spacer(Modifier.height(8.dp))
        Button(
            enabled = !busy,
            onClick = {
                busy = true
                scope.launch {
                    // Revoke the token on the server; sign out here whatever it answers.
                    runCatching { app.api().signOut(account.deviceId) }
                    app.signOut()
                }
            },
        ) { Text("Sign out") }
        Spacer(Modifier.height(24.dp))
        Text("Ferrocine for Android TV ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
    }
}
