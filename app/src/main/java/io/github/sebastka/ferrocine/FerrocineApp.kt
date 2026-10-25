package io.github.sebastka.ferrocine

import android.app.Application
import android.content.Context
import android.os.Build
import android.provider.Settings
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import io.github.sebastka.ferrocine.api.Api
import io.github.sebastka.ferrocine.api.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Who the app is signed in as, on which server. */
data class Account(val server: HttpUrl, val token: String, val deviceId: Long, val userName: String)

/**
 * The app's state: one HTTP client, the account (kept in private
 * preferences, excluded from backups), and the API for it.
 */
class FerrocineApp : Application(), SingletonImageLoader.Factory {
    private val prefs by lazy { getSharedPreferences("account", Context.MODE_PRIVATE) }

    /** Requests to the account's server carry its token; others go out bare. */
    val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            // The playlist request waits up to 30 s for ffmpeg.
            .readTimeout(45, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val req = chain.request()
                val a = account.value
                val sameServer = a != null && req.url.host == a.server.host && req.url.port == a.server.port
                if (sameServer && req.header("Authorization") == null) {
                    chain.proceed(req.newBuilder().header("Authorization", "Bearer ${a.token}").build())
                } else {
                    chain.proceed(req)
                }
            }
            .build()
    }

    private val _account = MutableStateFlow<Account?>(null)
    val account: StateFlow<Account?> get() = _account

    /** For work that outlives a screen (the last progress report, stopping a transcode). */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Settings of this TV, kept across sign-outs. */
    private val settings by lazy { getSharedPreferences("settings", Context.MODE_PRIVATE) }

    /**
     * Formats sent undecoded to the HDMI sink when it claims them. Off by
     * default for DTS and TrueHD: many TVs claim them and then play noise.
     */
    fun passthrough(format: String): Boolean = settings.getBoolean("passthrough_$format", false)

    fun setPassthrough(format: String, on: Boolean) {
        settings.edit().putBoolean("passthrough_$format", on).apply()
    }

    /** A server typed but not signed in to yet. */
    var pendingServer: HttpUrl? = null

    override fun onCreate() {
        super.onCreate()
        val server = prefs.getString("server", null)?.toHttpUrlOrNull()
        val token = prefs.getString("token", null)
        if (server != null && token != null) {
            _account.value = Account(server, token, prefs.getLong("device_id", 0), prefs.getString("user", "").orEmpty())
        }
    }

    fun api(): Api {
        val a = account.value
        return if (a != null) Api(a.server, http, a.token) else Api(pendingServer!!, http, null)
    }

    fun signIn(server: HttpUrl, token: String, deviceId: Long, user: User) {
        prefs.edit()
            .putString("server", server.toString())
            .putString("token", token)
            .putLong("device_id", deviceId)
            .putString("user", user.shownName)
            .apply()
        _account.value = Account(server, token, deviceId, user.shownName)
    }

    fun signOut() {
        pendingServer = account.value?.server
        prefs.edit().clear().apply()
        _account.value = null
    }

    /** The name the device is paired under: what the user named the TV. */
    fun deviceName(): String =
        Settings.Global.getString(contentResolver, "device_name")?.takeIf { it.isNotBlank() }
            ?: Build.MODEL

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { http })) }
            .diskCache {
                DiskCache.Builder().directory(context.cacheDir.resolve("images")).maxSizeBytes(256L shl 20).build()
            }
            .build()
}
