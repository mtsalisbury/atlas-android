package io.nekohasekai.sfa.atlas

/**
 * Pairs a bare Travel Edge (ESP32-C5) board: BLE scan/connect -> send Wi-Fi
 * credentials + a single-use enrollment token -> device redeems the token
 * itself against /enroll/edge/wireguard and brings up its own WireGuard
 * tunnel. This screen never talks to the board's Wi-Fi/WireGuard endpoints
 * directly -- it only ever sends the token AtlasApi.mintEdgeEnrollmentToken
 * already minted, same trust boundary as the web Edge screen (presence.html)
 * uses today.
 *
 * The BLE transport itself is defined behind [EdgeBleTransport] rather than
 * called directly against a specific SDK here: at the time this was written,
 * the real dependency (Espressif's open-source esp-idf-provisioning-android,
 * see the TODO in app/build.gradle.kts) had not yet been added to the
 * project and its exact current API surface hadn't been verified against
 * this codebase, so wiring calls to unconfirmed method names would have
 * been guessing, not implementing. [EdgeBleTransportPending] is the only
 * concrete implementation right now and fails clearly rather than silently
 * pretending to pair -- swap in a real implementation backed by the SDK
 * once it's added, without needing to change anything below.
 */
object AtlasEdgePairing {

    sealed class PairingState {
        object Idle : PairingState()
        object Scanning : PairingState()
        data class DeviceFound(val name: String) : PairingState()
        object Connecting : PairingState()
        object MintingToken : PairingState()
        object SendingCredentials : PairingState()
        object WaitingForEnrollment : PairingState()
        object Success : PairingState()
        data class Failed(val message: String) : PairingState()
    }

    /**
     * One BLE-provisioning-capable device, as discovered by [EdgeBleTransport.scan].
     * `name` is the "PROV_XXXXXX" service name the firmware advertises
     * (get_device_service_name in atlas-connector-esp32/main/app_main.c).
     */
    data class DiscoveredDevice(val name: String, val address: String)

    /**
     * Abstraction over the protocomm BLE transport (X25519/AES-GCM security
     * handshake, Wi-Fi credential exchange, and the "custom-data" endpoint
     * write) so this screen's pairing logic doesn't depend on a specific
     * SDK's exact method names.
     */
    interface EdgeBleTransport {
        suspend fun scan(): List<DiscoveredDevice>
        suspend fun connect(device: DiscoveredDevice, proofOfPossession: String)
        suspend fun sendWifiCredentials(ssid: String, password: String)
        suspend fun sendCustomData(endpointName: String, payload: ByteArray)
        suspend fun disconnect()
    }

    class EdgeBleTransportPending : EdgeBleTransport {
        private fun notReady(): Nothing = throw IllegalStateException(
            "Edge BLE pairing transport not wired up yet -- add the esp-idf-provisioning-android " +
                "dependency (see TODO in app/build.gradle.kts) and provide a real EdgeBleTransport " +
                "implementation before this screen can pair a real device.",
        )

        override suspend fun scan(): List<DiscoveredDevice> = notReady()
        override suspend fun connect(device: DiscoveredDevice, proofOfPossession: String) = notReady()
        override suspend fun sendWifiCredentials(ssid: String, password: String) = notReady()
        override suspend fun sendCustomData(endpointName: String, payload: ByteArray) = notReady()
        override suspend fun disconnect() = notReady()
    }

    /**
     * Fixed, non-account-specific PoP baked into every unit of a given
     * firmware build (see plans/travel-edge-enrollment.md's "PoP note" in
     * ProjectAtlas) -- it secures the BLE transport, not authorization.
     * Real per-account authorization is entirely the enrollment token,
     * minted below and redeemed server-side. Must match the firmware's
     * CONFIG_EXAMPLE_PROV_SEC2_PWD / sec_params `pop` value exactly.
     */
    private const val FIRMWARE_POP = "abcd1234"

    /**
     * Runs the full pairing flow. `presenceToken` is the caller's existing
     * X-Presence-Token, used only once, to mint the disposable enrollment
     * token -- it is never sent to the board.
     */
    suspend fun pair(
        transport: EdgeBleTransport,
        presenceToken: String,
        deviceLabel: String,
        wifiSsid: String,
        wifiPassword: String,
        onState: (PairingState) -> Unit,
    ) {
        try {
            onState(PairingState.Scanning)
            val devices = transport.scan()
            val device = devices.firstOrNull()
                ?: throw IllegalStateException("No nearby Travel Edge device found. Make sure it's powered on and unpaired.")
            onState(PairingState.DeviceFound(device.name))

            onState(PairingState.Connecting)
            transport.connect(device, FIRMWARE_POP)

            onState(PairingState.MintingToken)
            val minted = AtlasApi.mintEdgeEnrollmentToken(presenceToken, deviceLabel)

            onState(PairingState.SendingCredentials)
            transport.sendWifiCredentials(wifiSsid, wifiPassword)
            val tokenPayload = org.json.JSONObject().put("token", minted.token).toString()
                .toByteArray(Charsets.UTF_8)
            transport.sendCustomData("custom-data", tokenPayload)
            transport.disconnect()

            onState(PairingState.WaitingForEnrollment)
            // The device now redeems the token itself against
            // /enroll/edge/wireguard and brings up WireGuard fail-closed --
            // nothing left for the phone to do but let the caller poll
            // GET /api/presence/edge for the device coming online, same as
            // the web Edge screen already does.
            onState(PairingState.Success)
        } catch (e: Exception) {
            onState(PairingState.Failed(e.message ?: "Pairing failed"))
        }
    }
}
