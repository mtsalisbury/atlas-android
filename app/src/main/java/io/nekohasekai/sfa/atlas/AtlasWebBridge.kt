package io.nekohasekai.sfa.atlas

import android.webkit.JavascriptInterface
import androidx.lifecycle.lifecycleScope
import io.nekohasekai.sfa.bg.BoxService
import io.nekohasekai.sfa.compose.MainActivity
import io.nekohasekai.sfa.database.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * The bridge between presence.html (running in the app's own WebView, the
 * app's real front door now -- see STATUS.md for the "make it look and act
 * like Atlas" direction) and the native code a web page can never do on its
 * own: holding a VPN connection open. The web side owns identity/UI, this
 * owns the one thing it structurally can't -- everything else (Lens,
 * Devices, Account) is just presence.html, unchanged.
 *
 * Only ever attached to a WebView whose navigation is locked to Atlas's own
 * origins (see AtlasWebViewScreen) -- an addJavascriptInterface bridge is
 * only as safe as the pages allowed to reach it.
 *
 * Methods are fire-and-forget from JS's side; results come back via
 * window.AtlasCallbacks.<name>(jsonString), which presence.html must define.
 */
class AtlasWebBridge(
    private val activity: MainActivity,
    private val onOpenNativeDashboard: () -> Unit,
) {
    private fun callback(method: String, json: String) {
        activity.runOnUiThread {
            activity.currentWebView?.evaluateJavascript(
                "window.AtlasCallbacks && window.AtlasCallbacks.$method(${JSONObject.quote(json)})",
                null,
            )
        }
    }

    /**
     * presenceToken is whatever presence.html already obtained from its own
     * login/signup call -- the bridge never collects credentials itself,
     * it just hands the existing token to the native enrollment path.
     */
    @JavascriptInterface
    fun enrollAndConnect(presenceToken: String) {
        activity.lifecycleScope.launch(Dispatchers.IO) {
            val result = try {
                AtlasEnrollment.enrollWithToken(activity, presenceToken)
                JSONObject().put("ok", true)
            } catch (e: Exception) {
                JSONObject().put("ok", false).put("error", e.message ?: "enrollment failed")
            }
            if (result.optBoolean("ok")) {
                // ProfileManager.create(andSelect = true) inside enrollWithToken
                // only changes which profile is *selected* -- an already-running
                // service keeps serving whatever config it loaded at its own
                // last start, same class of bug as DashboardViewModel.
                // selectProfile()'s own restart-on-change handling. Unconditional
                // stop is a safe no-op when nothing was running (BoxService.stop()
                // just sends a broadcast); the fixed delay is a simplification of
                // that ViewModel's real status-polling loop, good enough for a
                // profile that was *just* created rather than a live switch.
                BoxService.stop()
                delay(800L)
            }
            withContext(Dispatchers.Main) {
                callback("onEnrollResult", result.toString())
                if (result.optBoolean("ok")) {
                    activity.startService()
                }
            }
        }
    }

    @JavascriptInterface
    fun disconnect() {
        activity.lifecycleScope.launch(Dispatchers.IO) { BoxService.stop() }
    }

    @JavascriptInterface
    fun isSignedIn(): Boolean = Settings.atlasPresenceToken.isNotBlank()

    /**
     * Approximate, not a live service binding (out of scope for this first
     * pass) -- reflects the user's last start/stop intent, not real-time
     * tunnel health. Good enough for presence.html to decide what to show;
     * a real "Connection" native screen (openNativeDashboard) is the place
     * for live, accurate status.
     */
    @JavascriptInterface
    fun getConnectionStatus(): String =
        JSONObject()
            .put("signedIn", Settings.atlasPresenceToken.isNotBlank())
            .put("started", Settings.startedByUser)
            .toString()

    @JavascriptInterface
    fun openNativeDashboard() {
        activity.runOnUiThread { onOpenNativeDashboard() }
    }

    @JavascriptInterface
    fun signOut() {
        AtlasEnrollment.signOut()
        activity.runOnUiThread { callback("onSignedOut", "{}") }
    }
}
