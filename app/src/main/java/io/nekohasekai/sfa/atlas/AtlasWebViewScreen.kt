package io.nekohasekai.sfa.atlas

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.webkit.WebResourceRequest
import android.webkit.WebResourceError
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.nekohasekai.sfa.compose.MainActivity
import io.nekohasekai.sfa.constant.Status
import io.nekohasekai.sfa.database.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

/**
 * The app's real front door: presence.html itself, not a native
 * reimplementation of it. Only Atlas's own origins may ever load here --
 * addJavascriptInterface's bridge is only as safe as what's allowed to
 * reach it, so navigation to anything else opens in a real external
 * browser instead of following the link in-app.
 */
private val ALLOWED_HOSTS = setOf(
    "presence.layer9i.com",
    "dashboard.layer9i.com",
)

private const val START_URL = "https://presence.layer9i.com/presence.html"
private const val HEALTH_URL = "https://presence.layer9i.com/health"

private enum class AtlasAndroidHealth(val label: String, val color: Color) {
    Checking("Checking connection", Color(0xFF5659DD)),
    Healthy("Atlas is healthy", Color(0xFF16A34A)),
    Stalled("Traffic stalled", Color(0xFFDC2626)),
    Disconnected("Atlas is disconnected", Color(0xFF6B7280)),
}

private suspend fun atlasHealthReachable(): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        (URL(HEALTH_URL).openConnection() as HttpURLConnection).run {
            requestMethod = "HEAD"
            connectTimeout = 1_500
            readTimeout = 1_500
            useCaches = false
            responseCode in 200..499
        }
    }.getOrDefault(false)
}

private fun networkDescription(context: Context): String {
    val manager = context.getSystemService(ConnectivityManager::class.java)
    val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return "unavailable"
    return when {
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "vpn"
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
        else -> "other"
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AtlasWebViewScreen(
    activity: MainActivity,
    onOpenNativeDashboard: () -> Unit,
) {
    val context = LocalContext.current
    var webView by remember { mutableStateOf<WebView?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var connectionHealth by remember { mutableStateOf(AtlasAndroidHealth.Checking) }

    LaunchedEffect(Unit) {
        while (true) {
            connectionHealth = when (activity.atlasServiceStatus()) {
                Status.Started -> if (atlasHealthReachable()) {
                    AtlasAndroidHealth.Healthy
                } else {
                    AtlasAndroidHealth.Stalled
                }
                Status.Starting -> AtlasAndroidHealth.Checking
                Status.Stopping, Status.Stopped -> AtlasAndroidHealth.Disconnected
            }
            delay(2_000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                color = connectionHealth.color,
            ) {
                Text(
                    text = connectionHealth.label,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    textAlign = TextAlign.Center,
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Button(onClick = {
                val report = buildString {
                    appendLine("Atlas Android connection report")
                    appendLine("Captured: ${Instant.now()}")
                    appendLine("Health: ${connectionHealth.label}")
                    appendLine("VPN requested: ${Settings.startedByUser}")
                    appendLine("Network: ${networkDescription(context)}")
                    appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                    appendLine("Portal error: ${loadError ?: "none"}")
                }
                context.startActivity(Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "Atlas connection report")
                        putExtra(Intent.EXTRA_TEXT, report)
                    },
                    "Share Atlas report",
                ))
            }) {
                Text("Capture Problem")
            }
        }

        Box(modifier = Modifier.weight(1f)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                WebView(context).apply {
                    // Keep the platform WebView in the same compositing layer
                    // as the Compose health controls. On the emulator the
                    // hardware layer otherwise paints an opaque white surface
                    // over sibling Compose content outside its measured bounds.
                    setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
                    addJavascriptInterface(
                        AtlasWebBridge(activity, onOpenNativeDashboard),
                        "AtlasBridge",
                    )
                    webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest,
                    ): Boolean {
                        val host = request.url.host ?: return true
                        if (host in ALLOWED_HOSTS) return false
                        // Not an Atlas page -- never load it in a WebView that
                        // holds a privileged bridge. Hand it to a real browser.
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, request.url))
                        }
                        return true
                    }

                    override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                        loadError = null
                    }

                    override fun onReceivedError(
                        view: WebView,
                        request: WebResourceRequest,
                        error: WebResourceError,
                    ) {
                        if (request.isForMainFrame) {
                            loadError = "${error.description} (${error.errorCode})"
                            android.util.Log.e("AtlasWebView", "Portal load failed: $loadError")
                        }
                    }

                    override fun onReceivedHttpError(
                        view: WebView,
                        request: WebResourceRequest,
                        errorResponse: WebResourceResponse,
                    ) {
                        if (request.isForMainFrame) {
                            loadError = "Atlas returned HTTP ${errorResponse.statusCode}."
                            android.util.Log.e("AtlasWebView", "Portal HTTP failure: $loadError")
                        }
                    }
                }
                loadUrl(START_URL, mapOf("Cache-Control" to "no-cache", "Pragma" to "no-cache"))
                activity.currentWebView = this
                webView = this
            }
            },
        )

        loadError?.let { error ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("Atlas couldn't load", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = error,
                    modifier = Modifier.padding(vertical = 12.dp),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = {
                    loadError = null
                    webView?.loadUrl(
                        START_URL,
                        mapOf("Cache-Control" to "no-cache", "Pragma" to "no-cache"),
                    )
                }) {
                    Text("Retry Atlas")
                }
            }
        }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            activity.currentWebView = null
        }
    }
}
