package com.benekapps.myslnik.ui

import android.app.Activity
import com.benekapps.myslnik.support.SupportBilling
import com.benekapps.myslnik.support.SupportEvent
import com.benekapps.myslnik.support.SupportProduct
import com.benekapps.myslnik.support.SupportState
import com.benekapps.myslnik.ui.support.SupportViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SupportViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    private class FakeBilling : SupportBilling {
        override val state = MutableStateFlow<SupportState>(SupportState.Loading)
        override val events = MutableSharedFlow<SupportEvent>(extraBufferCapacity = 4)
        var connected = false
        override fun connect() {
            connected = true
        }
        override fun launch(activity: Activity, productId: String) = Unit
        override fun close() = Unit
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `connects at start and shows products and thanks`() = runTest(dispatcher) {
        val billing = FakeBilling()
        val vm = SupportViewModel(billing)
        assertTrue(billing.connected)
        billing.state.value = SupportState.Ready(listOf(SupportProduct("support_small", "4,99 zł")))
        assertEquals("4,99 zł", (vm.state.value as SupportState.Ready).products.single().price)
        billing.events.emit(SupportEvent.THANKS)
        assertEquals(SupportEvent.THANKS, vm.event.value)
        vm.eventShown()
        assertNull(vm.event.value)
    }

    @Test
    fun `product ids match the play console setup`() {
        assertEquals(listOf("support_small", "support_medium", "support_large"), SupportBilling.PRODUCT_IDS)
    }
}
