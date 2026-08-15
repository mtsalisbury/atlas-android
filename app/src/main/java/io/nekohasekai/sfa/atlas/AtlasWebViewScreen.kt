package io.nekohasekai.sfa.atlas

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceError
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.nekohasekai.sfa.compose.MainActivity

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

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AtlasWebViewScreen(
    activity: MainActivity,
    onOpenNativeDashboard: () -> Unit,
) {
    val context = LocalContext.current
    var webView by remember { mutableStateOf<WebView?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                WebView(context).apply {
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

    DisposableEffect(Unit) {
        onDispose {
            activity.currentWebView = null
        }
    }
}
