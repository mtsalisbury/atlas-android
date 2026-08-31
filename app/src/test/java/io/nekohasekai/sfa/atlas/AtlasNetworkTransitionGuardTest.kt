package io.nekohasekai.sfa.atlas

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AtlasNetworkTransitionGuardTest {
    @Test fun firstNetworkAndSameTransportDoNotRefresh() {
        val guard = AtlasNetworkTransitionGuard()
        assertFalse(guard.observe(AtlasNetworkTransitionGuard.Transport.WIFI, true))
        assertFalse(guard.observe(AtlasNetworkTransitionGuard.Transport.WIFI, true))
    }

    @Test fun wifiCellularAndCellularWifiEachRefreshAfterSuccess() {
        val guard = AtlasNetworkTransitionGuard()
        guard.observe(AtlasNetworkTransitionGuard.Transport.WIFI, true)
        assertTrue(guard.observe(AtlasNetworkTransitionGuard.Transport.CELLULAR, true))
        guard.refreshSucceeded()
        assertTrue(guard.observe(AtlasNetworkTransitionGuard.Transport.WIFI, true))
    }

    @Test fun duplicateCallbacksAreCoalescedWhileRefreshIsRunning() {
        val guard = AtlasNetworkTransitionGuard()
        guard.observe(AtlasNetworkTransitionGuard.Transport.WIFI, true)
        assertTrue(guard.observe(AtlasNetworkTransitionGuard.Transport.CELLULAR, true))
        assertFalse(guard.observe(AtlasNetworkTransitionGuard.Transport.WIFI, true))
    }

    @Test fun stoppedServiceNeverRefreshesAndResetClearsHistory() {
        val guard = AtlasNetworkTransitionGuard()
        guard.observe(AtlasNetworkTransitionGuard.Transport.WIFI, false)
        assertFalse(guard.observe(AtlasNetworkTransitionGuard.Transport.CELLULAR, false))
        guard.reset()
        assertFalse(guard.observe(AtlasNetworkTransitionGuard.Transport.WIFI, true))
    }
}
