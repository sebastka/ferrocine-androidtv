package io.github.sebastka.ferrocine.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import io.github.sebastka.ferrocine.R
import io.github.sebastka.ferrocine.api.Api
import io.github.sebastka.ferrocine.api.ApiException
import io.github.sebastka.ferrocine.api.Pairing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** First step: the server's address, checked before pairing. */
@Composable
fun ServerScreen(onServer: () -> Unit) {
    val app = app()
    var text by remember { mutableStateOf(app.pendingServer?.toString()?.trimEnd('/') ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val field = remember { FocusRequester() }

    fun connect() {
        val url = Api.parseServer(text)
        if (url == null) {
            error = "Enter the address you open Ferrocine at, e.g. media.example.com."
            return
        }
        checking = true
        error = null
        scope.launch {
            val ok = Api(url, app.http, null).healthy()
            checking = false
            if (ok) {
                app.pendingServer = url
                onServer()
            } else {
                error = "No Ferrocine server answers at $url."
            }
        }
    }

    LaunchedEffect(Unit) { field.requestFocus() }
    Row(Modifier.fillMaxSize().padding(64.dp), verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(R.drawable.logo), contentDescription = null, modifier = Modifier.size(120.dp))
        Spacer(Modifier.width(48.dp))
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Welcome to Ferrocine", style = MaterialTheme.typography.headlineMedium)
            Text("Server address", style = MaterialTheme.typography.titleMedium)
            BasicTextField(
                value = text,
                onValueChange = { text = it; error = null },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(Accent),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { connect() }),
                modifier = Modifier.width(560.dp)
                    .focusRequester(field)
                    .onFocusChanged { focused = it.isFocused }
                    .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)
                    .border(2.dp, if (focused) Accent else Color.Transparent, MaterialTheme.shapes.medium)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                decorationBox = { inner ->
                    if (text.isEmpty()) {
                        Text("media.example.com", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            style = MaterialTheme.typography.titleLarge)
                    }
                    inner()
                },
            )
            if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
            if (text.trim().startsWith("http://")) {
                Text(
                    "Plain http is only safe on your home network: the device token travels unencrypted.",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Button(onClick = { connect() }, enabled = !checking) { Text(if (checking) "Connecting…" else "Continue") }
        }
    }
}

/** A QR code of [text], black on white. */
fun qrCode(text: String, size: Int = 512): Bitmap {
    val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(com.google.zxing.EncodeHintType.MARGIN to 1))
    val px = IntArray(size * size) { if (m[it % size, it / size]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
    return Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888)
}

/** Second step: Quick Connect. Shows a code and a QR code, polls until approved. */
@Composable
fun PairScreen(onBack: () -> Unit) {
    val app = app()
    var pairing by remember { mutableStateOf<Pairing?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var round by remember { mutableIntStateOf(0) }
    val server = app.pendingServer ?: return

    LaunchedEffect(round) {
        val api = Api(server, app.http, null)
        error = null
        pairing = null
        val p = try {
            api.pair(app.deviceName(), "androidtv")
        } catch (e: Exception) {
            error = errorMessage(e)
            return@LaunchedEffect
        }
        pairing = p
        val until = System.currentTimeMillis() + p.expiresIn * 1000
        while (System.currentTimeMillis() < until) {
            delay(p.interval * 1000)
            try {
                val granted = api.pairToken(p.secret) ?: continue
                app.signIn(server, granted.accessToken, granted.device.id, granted.user)
                return@LaunchedEffect
            } catch (e: ApiException) {
                // Expired or used: a new code.
                break
            } catch (e: Exception) {
                // The network blinked: keep polling.
            }
        }
        round++
    }

    Row(Modifier.fillMaxSize().padding(64.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Sign in with Quick Connect", style = MaterialTheme.typography.headlineMedium)
            Text(
                "On your phone or computer, signed in to Ferrocine, scan the code or open",
                style = MaterialTheme.typography.bodyLarge,
            )
            // The server's public URL (what browsers open), once known.
            val page = pairing?.approveUrl?.substringBefore('?') ?: "${server.toString().trimEnd('/')}/pair"
            Text(page, style = MaterialTheme.typography.titleLarge, color = Accent)
            Text("and enter:", style = MaterialTheme.typography.bodyLarge)
            val p = pairing
            when {
                error != null -> {
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Button(onClick = { round++ }) { Text("Try again") }
                        Button(onClick = onBack) { Text("Change server") }
                    }
                }
                p == null -> Text("…", fontSize = 56.sp)
                else -> {
                    Text(p.code, fontSize = 64.sp, letterSpacing = 6.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        "This TV will appear as “${app.deviceName()}”. Waiting for approval…",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onBack) { Text("Change server") }
                }
            }
        }
        val p = pairing
        if (p != null) {
            val bitmap = remember(p.approveUrl) { qrCode(p.approveUrl).asImageBitmap() }
            Box(Modifier.size(280.dp).background(Color.White, MaterialTheme.shapes.medium).padding(12.dp)) {
                Image(bitmap, contentDescription = "QR code of the approval page", modifier = Modifier.fillMaxSize())
            }
        }
    }
}
