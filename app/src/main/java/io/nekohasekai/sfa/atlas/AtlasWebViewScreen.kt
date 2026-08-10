package io.nekohasekai.sfa.atlas

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = {
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
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
                }
                loadUrl(START_URL)
                activity.currentWebView = this
            }
        },
    )

    DisposableEffect(Unit) {
        onDispose {
            activity.currentWebView = null
        }
    }
}
