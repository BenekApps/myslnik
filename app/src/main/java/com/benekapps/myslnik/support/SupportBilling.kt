package com.benekapps.myslnik.support

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/** One support level; [price] is formatted by Google Play in the user's currency. */
data class SupportProduct(val id: String, val price: String)

sealed interface SupportState {
    data object Loading : SupportState

    /** No Google Play (e.g. F-Droid build, no account) or the products are not set up. */
    data object Unavailable : SupportState
    data class Ready(val products: List<SupportProduct>) : SupportState
}

enum class SupportEvent { THANKS, PENDING, FAILED }

/** Voluntary support through Google Play. It unlocks nothing. */
interface SupportBilling {
    val state: StateFlow<SupportState>
    val events: SharedFlow<SupportEvent>
    fun connect()
    fun launch(activity: Activity, productId: String)
    fun close()

    companion object {
        /** One-time products to create in Play Console (prices are set there). */
        val PRODUCT_IDS = listOf("support_small", "support_medium", "support_large")
    }
}

/**
 * Google Play Billing for the support products. Purchases are consumed right
 * away, so the same level can be bought again and nothing stays "owned".
 * Billing talks to the Play Store app over IPC; the app itself still has no
 * INTERNET permission. No purchase data is stored or sent anywhere.
 */
class PlaySupportBilling(context: Context) : SupportBilling, PurchasesUpdatedListener {
    private val _state = MutableStateFlow<SupportState>(SupportState.Loading)
    override val state: StateFlow<SupportState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<SupportEvent>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val events: SharedFlow<SupportEvent> = _events.asSharedFlow()

    // Filled from billing callbacks (background thread), read on the main thread.
    private val details = ConcurrentHashMap<String, ProductDetails>()

    private val client: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    override fun connect() {
        if (client.isReady) return loadProducts()
        _state.value = SupportState.Loading
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingResponseCode.OK) {
                    loadProducts()
                    consumeLeftovers()
                } else {
                    _state.value = SupportState.Unavailable
                }
            }

            override fun onBillingServiceDisconnected() {
                _state.value = SupportState.Unavailable
            }
        })
    }

    private fun loadProducts() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                SupportBilling.PRODUCT_IDS.map {
                    QueryProductDetailsParams.Product.newBuilder().setProductId(it).setProductType(ProductType.INAPP).build()
                },
            )
            .build()
        client.queryProductDetailsAsync(params) { result, found ->
            if (result.responseCode != BillingResponseCode.OK) {
                _state.value = SupportState.Unavailable
                return@queryProductDetailsAsync
            }
            details.clear()
            found.productDetailsList.forEach { details[it.productId] = it }
            val products = SupportBilling.PRODUCT_IDS.mapNotNull { id ->
                details[id]?.let { d -> price(d)?.let { SupportProduct(id, it) } }
            }
            _state.value = if (products.isEmpty()) SupportState.Unavailable else SupportState.Ready(products)
        }
    }

    private fun price(d: ProductDetails): String? = d.oneTimePurchaseOfferDetails?.formattedPrice

    override fun launch(activity: Activity, productId: String) {
        val d = details[productId] ?: return
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d).build()))
            .build()
        val result = client.launchBillingFlow(activity, params)
        if (result.responseCode != BillingResponseCode.OK) _events.tryEmit(SupportEvent.FAILED)
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingResponseCode.OK -> purchases?.forEach { handle(it, notify = true) }
            BillingResponseCode.USER_CANCELED -> Unit
            // A previous support purchase was not consumed yet: consume it and try again.
            BillingResponseCode.ITEM_ALREADY_OWNED -> consumeLeftovers()
            else -> _events.tryEmit(SupportEvent.FAILED)
        }
    }

    private fun handle(purchase: Purchase, notify: Boolean) {
        if (purchase.products.none { it in SupportBilling.PRODUCT_IDS }) return
        when (purchase.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> {
                // Consuming also acknowledges the purchase (otherwise refunded after 3 days).
                client.consumeAsync(ConsumeParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()) { r, _ ->
                    if (notify && r.responseCode == BillingResponseCode.OK) _events.tryEmit(SupportEvent.THANKS)
                }
            }
            Purchase.PurchaseState.PENDING -> if (notify) _events.tryEmit(SupportEvent.PENDING)
            else -> Unit
        }
    }

    /** Purchases completed while the screen was closed (pending payments, app killed). */
    private fun consumeLeftovers() {
        client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(ProductType.INAPP).build()) { r, list ->
            if (r.responseCode == BillingResponseCode.OK) list.forEach { handle(it, notify = false) }
        }
    }

    override fun close() {
        client.endConnection()
    }
}
