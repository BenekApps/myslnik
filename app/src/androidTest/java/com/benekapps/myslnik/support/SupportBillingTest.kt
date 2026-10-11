package com.benekapps.myslnik.support

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Billing must start and settle without its excluded telemetry library.
 * On test devices without Play Store or without the products this ends in
 * Unavailable; the point is that it gets there without crashing.
 */
@RunWith(AndroidJUnit4::class)
class SupportBillingTest {
    @Test
    fun connectsWithoutTelemetryLibrary() = runBlocking {
        lateinit var billing: PlaySupportBilling
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            billing = PlaySupportBilling(ApplicationProvider.getApplicationContext())
            billing.connect()
        }
        val settled = withTimeout(15_000) { billing.state.first { it != SupportState.Loading } }
        assertNotEquals(SupportState.Loading, settled)
        InstrumentationRegistry.getInstrumentation().runOnMainSync { billing.close() }
    }
}
