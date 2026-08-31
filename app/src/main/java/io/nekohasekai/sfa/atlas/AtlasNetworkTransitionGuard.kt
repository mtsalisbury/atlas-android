package io.nekohasekai.sfa.atlas

/** Pure state machine for deciding when a physical-network change needs one
 * Atlas configuration refresh. Kept Android-free so the handoff rules have
 * fast JVM regression coverage. */
class AtlasNetworkTransitionGuard {
    enum class Transport { WIFI, CELLULAR }

    private var last: Transport? = null
    private var refreshInFlight = false

    fun reset() {
        last = null
        refreshInFlight = false
    }

    fun observe(transport: Transport, serviceStarted: Boolean): Boolean {
        val previous = last
        last = transport
        if (!serviceStarted || previous == null || previous == transport || refreshInFlight) return false
        refreshInFlight = true
        return true
    }

    fun refreshSucceeded() {
        refreshInFlight = false
    }
}
