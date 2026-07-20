package com.intelligame.huntix.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*

/**
 * BillingManager — Gestione acquisti Google Play Billing Library v7.
 *
 * Prodotti:
 * - Consumabili: pacchetti MVC (mvc_500, mvc_2000, mvc_5000, mvc_12000, mvc_30000, mvc_100000)
 * - Abbonamento: VIP Pass (vip_monthly)
 * - Una tantum: Season Pass (season_pass), Multiplayer Pro (multiplayer_pro)
 */
object BillingManager {

    private const val TAG = "BillingManager"
    private var billingClient: BillingClient? = null
    private var appContext: Context? = null
    private val onPurchaseComplete = mutableMapOf<String, (Boolean, String) -> Unit>()
    private var pendingProductId: String? = null

    // ── Product IDs (da creare su Google Play Console) ──────────
    data class MvcPackage(
        val productId: String,
        val mvcAmount: Int,
        val displayName: String,
        val emoji: String,
        val bonus: String
    )

    val MVC_PACKAGES = listOf(
        MvcPackage("mvc_500",    500,    "Starter",  "\uD83E\uDE99", ""),
        MvcPackage("mvc_2000",   2000,   "Base",     "\uD83D\uDCB0", "+33%"),
        MvcPackage("mvc_5000",   5000,   "Pro",      "\uD83D\uDC8E", "+67% \u2B50"),
        MvcPackage("mvc_12000",  12000,  "Elite",    "\uD83D\uDC51", "+100%"),
        MvcPackage("mvc_30000",  30000,  "Legend",   "\uD83C\uDFC6", "+150%"),
        MvcPackage("mvc_100000", 100000, "Mega",     "\uD83D\uDC33", "+200%")
    )

    const val PRODUCT_VIP_MONTHLY = "vip_monthly"
    const val PRODUCT_SEASON_PASS = "season_pass"
    const val PRODUCT_MULTIPLAYER = "multiplayer_pro"

    // ── Inizializzazione ────────────────────────────────────────

    fun init(context: Context) {
        appContext = context.applicationContext
        if (billingClient?.isReady == true) return

        billingClient = BillingClient.newBuilder(context)
            .setListener(purchasesUpdatedListener)
            .enablePendingPurchases()
            .build()

        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Billing connected")
                } else {
                    Log.e(TAG, "Billing setup failed: ${result.debugMessage}")
                }
            }
            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "Billing disconnected")
            }
        })
    }

    // ── Acquisto consumabile (pacchetto MVC) ────────────────────

    fun purchaseMvcPackage(activity: Activity, productId: String, onComplete: (Boolean, String) -> Unit) {
        onPurchaseComplete[productId] = onComplete
        pendingProductId = productId
        val client = billingClient ?: run { onComplete(false, "Billing non inizializzato"); return }

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(productId)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            )).build()

        client.queryProductDetailsAsync(params) { result, productDetailsList ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK || productDetailsList.isNullOrEmpty()) {
                onComplete(false, "Prodotto non trovato: ${result.debugMessage}")
                return@queryProductDetailsAsync
            }

            val productDetails = productDetailsList[0]
            val flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(productDetails)
                        .build()
                )).build()

            client.launchBillingFlow(activity, flowParams)
        }
    }

    // ── Acquisto abbonamento VIP ─────────────────────────────────

    fun purchaseVip(activity: Activity, onComplete: (Boolean, String) -> Unit) {
        onPurchaseComplete[PRODUCT_VIP_MONTHLY] = onComplete
        pendingProductId = PRODUCT_VIP_MONTHLY
        val client = billingClient ?: run { onComplete(false, "Billing non inizializzato"); return }

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(PRODUCT_VIP_MONTHLY)
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            )).build()

        client.queryProductDetailsAsync(params) { result, productDetailsList ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK || productDetailsList.isNullOrEmpty()) {
                onComplete(false, "Abbonamento non trovato: ${result.debugMessage}")
                return@queryProductDetailsAsync
            }

            val productDetails = productDetailsList[0]
            val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken ?: ""

            val flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(productDetails)
                        .setOfferToken(offerToken)
                        .build()
                )).build()

            client.launchBillingFlow(activity, flowParams)
        }
    }

    // ── Listener acquisti ────────────────────────────────────────

    private val purchasesUpdatedListener = PurchasesUpdatedListener { result, purchases ->
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach { purchase ->
                    handlePurchase(purchase)
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                pendingProductId?.let { onPurchaseComplete[it]?.invoke(false, "Acquisto annullato") }
                pendingProductId = null
            }
            else -> {
                pendingProductId?.let { onPurchaseComplete[it]?.invoke(false, "Errore: ${result.debugMessage}") }
                pendingProductId = null
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return

        // Acknowledge the purchase
        if (!purchase.isAcknowledged) {
            val ackParams = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            billingClient?.acknowledgePurchase(ackParams) { /* acknowledged */ }
        }

        val productId = purchase.products.firstOrNull() ?: ""

        // Check if it's a consumable MVC package
        val mvcPack = MVC_PACKAGES.find { it.productId == productId }
        if (mvcPack != null) {
            // Consume it so it can be bought again
            val consumeParams = ConsumeParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            billingClient?.consumeAsync(consumeParams) { _, _ ->
                onPurchaseComplete[productId]?.invoke(true, "mvc:${mvcPack.mvcAmount}")
            }
            return
        }

        // VIP subscription or one-time purchase -> activate the related perks
        when (productId) {
            PRODUCT_VIP_MONTHLY -> {
                appContext?.let { VipManager.syncVipStatus(it) }
                onPurchaseComplete[productId]?.invoke(true, "vip")
            }
            PRODUCT_SEASON_PASS -> {
                appContext?.let { SeasonPassManager.activate(it) }
                onPurchaseComplete[productId]?.invoke(true, "season")
            }
            PRODUCT_MULTIPLAYER -> {
                appContext?.let { MultiplayerProManager.activate(it) }
                onPurchaseComplete[productId]?.invoke(true, "multiplayer")
            }
            else -> onPurchaseComplete[productId]?.invoke(true, productId)
        }
        pendingProductId = null
    }

    // ── Verifica abbonamento attivo ──────────────────────────────

    fun checkVipStatus(onResult: (Boolean) -> Unit) {
        val client = billingClient ?: run { onResult(false); return }
        if (!client.isReady) { onResult(false); return }

        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        client.queryPurchasesAsync(params) { result, purchases ->
            val hasVip = purchases.any {
                it.products.contains(PRODUCT_VIP_MONTHLY) &&
                it.purchaseState == Purchase.PurchaseState.PURCHASED
            }
            onResult(hasVip)
        }
    }



    // ── Acquisto one-time (Season Pass, Multiplayer Pro) ────────

    fun purchaseOneTime(activity: Activity, productId: String, onComplete: (Boolean, String) -> Unit) {
        onPurchaseComplete[productId] = onComplete
        pendingProductId = productId
        val client = billingClient ?: run { onComplete(false, "Billing non inizializzato"); return }

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(productId)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            )).build()

        client.queryProductDetailsAsync(params) { result, productDetailsList ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK || productDetailsList.isNullOrEmpty()) {
                onComplete(false, "Prodotto non trovato: ${result.debugMessage}")
                return@queryProductDetailsAsync
            }
            val flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(productDetailsList[0])
                        .build()
                )).build()
            client.launchBillingFlow(activity, flowParams)
        }
    }

    // ── Query prezzi (per mostrare nella UI) ────────────────────

    fun queryMvcPrices(onResult: (Map<String, String>) -> Unit) {
        val client = billingClient ?: run { onResult(emptyMap()); return }

        val productList = MVC_PACKAGES.map {
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(it.productId)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        client.queryProductDetailsAsync(params) { _, detailsList ->
            val priceMap = mutableMapOf<String, String>()
            detailsList?.forEach { details ->
                val price = details.oneTimePurchaseOfferDetails?.formattedPrice ?: "N/A"
                priceMap[details.productId] = price
            }
            onResult(priceMap)
        }
    }

    fun queryVipPrice(onResult: (String) -> Unit) {
        val client = billingClient ?: run { onResult("N/A"); return }

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(PRODUCT_VIP_MONTHLY)
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            )).build()

        client.queryProductDetailsAsync(params) { _, detailsList ->
            val price = detailsList?.firstOrNull()
                ?.subscriptionOfferDetails?.firstOrNull()
                ?.pricingPhases?.pricingPhaseList?.firstOrNull()
                ?.formattedPrice ?: "N/A"
            onResult(price)
        }
    }
}
