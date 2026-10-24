package io.github.sebastka.ferrocine

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import io.github.sebastka.ferrocine.player.PlayerScreen
import io.github.sebastka.ferrocine.ui.Browse
import io.github.sebastka.ferrocine.ui.DetailsScreen
import io.github.sebastka.ferrocine.ui.FerrocineTheme
import io.github.sebastka.ferrocine.ui.PairScreen
import io.github.sebastka.ferrocine.ui.ServerScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as FerrocineApp
        setContent {
            FerrocineTheme {
                Surface(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    val account by app.account.collectAsStateWithLifecycle()
                    if (account == null) Setup() else Signed()
                }
            }
        }
    }
}

/** Not signed in: server, then Quick Connect. */
@androidx.compose.runtime.Composable
private fun Setup() {
    val nav = rememberNavController()
    NavHost(nav, startDestination = "server") {
        composable("server") { ServerScreen(onServer = { nav.navigate("pair") }) }
        composable("pair") { PairScreen(onBack = { nav.popBackStack() }) }
    }
}

@androidx.compose.runtime.Composable
private fun Signed() {
    val nav = rememberNavController()
    NavHost(nav, startDestination = "browse") {
        composable("browse") {
            Browse(onOpen = { nav.navigate("item/${it.id}") })
        }
        composable("item/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            DetailsScreen(
                id = entry.arguments!!.getLong("id"),
                onOpen = { nav.navigate("item/${it.id}") },
                onPlay = { item, resume -> nav.navigate("play/${item.id}?resume=$resume") },
            )
        }
        composable(
            "play/{id}?resume={resume}",
            arguments = listOf(
                navArgument("id") { type = NavType.LongType },
                navArgument("resume") { type = NavType.BoolType; defaultValue = true },
            ),
        ) { entry ->
            PlayerScreen(
                id = entry.arguments!!.getLong("id"),
                resume = entry.arguments!!.getBoolean("resume"),
                onExit = { nav.popBackStack() },
                onNext = { next ->
                    nav.popBackStack()
                    nav.navigate("play/${next.id}?resume=false")
                },
            )
        }
    }
}
