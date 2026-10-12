package www.daqtpi.com

import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
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
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.consumePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Relationship Referee — rebuilt natively so the $1 verdict can be sold with
 * real Google Play Billing (which the old AppCreator24 build could not do).
 *
 * Flow:
 *  1. Start screen -> "Get a Verdict" button -> Google Play payment sheet
 *     for the one-time product [PRODUCT_ID] ("verdict").
 *  2. On a successful purchase the purchase is acknowledged + consumed
 *     (so it can be bought again next time), and the referee form at
 *     [REFEREE_URL] opens in a WebView.
 *  3. On every app start, any leftover purchased-but-unconsumed "verdict"
 *     is finished off the same way, so an interrupted purchase is never lost.
 *
 * NOTE: there is no server — purchases are verified on the device only.
 * See README.md for the limits that come with that.
 */
class MainActivity : ComponentActivity(), PurchasesUpdatedListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var billingClient: BillingClient

    /** Cached product info for "verdict", filled in once billing connects. */
    private var verdictDetails: ProductDetails? = null

    // ---- UI state (read by the Compose screens) ----
    private var productPrice by mutableStateOf<String?>(null)
    private var statusText by mutableStateOf<String?>(null)
    private var buyEnabled by mutableStateOf(false)
    private var showWebView by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (showWebView) {
                        WebViewScreen(onBack = { showWebView = false })
                    } else {
                        StartScreen(
                            priceText = productPrice,
                            statusText = statusText,
                            buyEnabled = buyEnabled,
                            onBuyClick = { buyVerdict() }
                        )
                    }
                }
            }
        }

        setupBilling()
    }

    override fun onResume() {
        super.onResume()
        // If a payment was pending (e.g. a slow payment method) and has since
        // gone through, pick it up when the user comes back to the app.
        if (::billingClient.isInitialized && billingClient.isReady) {
            scope.launch { restorePendingPurchase() }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        if (::billingClient.isInitialized) {
            billingClient.endConnection()
        }
        super.onDestroy()
    }

    // ------------------------------------------------------------------
    // Billing setup
    // ------------------------------------------------------------------

    private fun setupBilling() {
        billingClient = BillingClient.newBuilder(this)
            .setListener(this)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .build()
            )
            .build()
        connectToBilling()
    }

    private fun connectToBilling() {
        if (billingClient.isReady) {
            onBillingReady()
            return
        }
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    onBillingReady()
                } else {
                    buyEnabled = false
                    statusText = "Couldn't connect to Google Play billing " +
                        "(code ${billingResult.responseCode}). " +
                        "Check your connection and reopen the app."
                }
            }

            override fun onBillingServiceDisconnected() {
                // Google Play dropped the connection — try to reconnect.
                connectToBilling()
            }
        })
    }

    private fun onBillingReady() {
        scope.launch {
            loadProductPrice()
            restorePendingPurchase()
        }
    }

    /** Ask Google Play for the "verdict" product so we can show its real price. */
    private suspend fun loadProductPrice() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
            )
            .build()

        val result = billingClient.queryProductDetails(params)
        val details = result.productDetailsList?.firstOrNull()

        if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK &&
            details != null
        ) {
            verdictDetails = details
            productPrice = details.oneTimePurchaseOfferDetails?.formattedPrice
            buyEnabled = true
            statusText = null
        } else {
            buyEnabled = false
            statusText = "The verdict product isn't available yet. Please try again later."
        }
    }

    /**
     * Look for a "verdict" purchase that was paid for but never finished
     * (app closed mid-purchase, payment pending, etc.) and deliver it.
     */
    private suspend fun restorePendingPurchase() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        val result = billingClient.queryPurchasesAsync(params)
        if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) return

        val pending = result.purchasesList.firstOrNull { purchase ->
            purchase.purchaseState == Purchase.PurchaseState.PURCHASED &&
                purchase.products.contains(PRODUCT_ID)
        } ?: return

        if (deliverVerdict(pending)) {
            toast("We found your earlier purchase — you're all set.")
        }
    }

    // ------------------------------------------------------------------
    // Buying
    // ------------------------------------------------------------------

    private fun buyVerdict() {
        val details = verdictDetails
        if (!billingClient.isReady || details == null) {
            toast("Billing isn't ready yet — give it a second and try again.")
            if (billingClient.isReady) {
                scope.launch { loadProductPrice() }
            } else {
                connectToBilling()
            }
            return
        }

        val productParamsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
        // One-time products can carry an offer token (special offers etc.).
        // Only set it when Google actually gave us one.
        details.oneTimePurchaseOfferDetails?.offerToken?.let { token ->
            productParamsBuilder.setOfferToken(token)
        }

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParamsBuilder.build()))
            .build()

        val result = billingClient.launchBillingFlow(this, flowParams)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            toast("Couldn't open the payment sheet (code ${result.responseCode}). Please try again.")
        }
    }

    /** Google Play calls this when a purchase finishes, fails, or is cancelled. */
    override fun onPurchasesUpdated(
        billingResult: BillingResult,
        purchases: List<Purchase>?
    ) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach { purchase ->
                    if (!purchase.products.contains(PRODUCT_ID)) return@forEach
                    when (purchase.purchaseState) {
                        Purchase.PurchaseState.PURCHASED -> scope.launch {
                            if (deliverVerdict(purchase)) {
                                toast("Payment complete — submit your argument below.")
                            }
                        }
                        Purchase.PurchaseState.PENDING -> {
                            statusText = "Payment is pending. Once Google confirms it, " +
                                "reopen the app and we'll let you straight in."
                        }
                    }
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                toast("Purchase cancelled — no charge made.")
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                // They already paid but it was never consumed — finish it off.
                scope.launch { restorePendingPurchase() }
            }
            else -> {
                toast("Purchase didn't go through (code ${billingResult.responseCode}). Please try again.")
            }
        }
    }

    /**
     * Acknowledge (if needed) and consume a paid "verdict" purchase, then
     * open the referee form. Consuming makes the product buyable again.
     * Returns true when everything succeeded.
     */
    private suspend fun deliverVerdict(purchase: Purchase): Boolean {
        if (!purchase.isAcknowledged) {
            val ackParams = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            val ackResult = billingClient.acknowledgePurchase(ackParams)
            if (ackResult.responseCode != BillingClient.BillingResponseCode.OK) {
                return false
            }
        }

        val consumeParams = ConsumeParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        val consumeResult = billingClient.consumePurchase(consumeParams)

        return if (consumeResult.billingResult.responseCode ==
            BillingClient.BillingResponseCode.OK
        ) {
            showWebView = true
            true
        } else {
            statusText = "Your payment went through but we couldn't finish setting up. " +
                "Reopen the app and we'll sort it out — you won't be charged again."
            false
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    companion object {
        /** Play Console product ID for the one-time "verdict" purchase. */
        private const val PRODUCT_ID = "verdict"

        /** The referee submission form (same page the old app pointed at). */
        private const val REFEREE_URL = "https://daqtpi.com/referee"
    }
}

// ----------------------------------------------------------------------
// Screens
// ----------------------------------------------------------------------

@Composable
private fun StartScreen(
    priceText: String?,
    statusText: String?,
    buyEnabled: Boolean,
    onBuyClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Relationship Referee",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Can't settle an argument? Tell the Referee your side and get a " +
                "fair, unbiased verdict delivered to your email. One verdict costs \$1 — " +
                "pay with Google Play below, then you'll go straight to the form.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(28.dp))

        Button(onClick = onBuyClick, enabled = buyEnabled) {
            Text(text = if (priceText != null) "Get a Verdict — $priceText" else "Get a Verdict — \$1")
        }

        when {
            !buyEnabled && statusText == null -> {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Connecting to Google Play…",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
            statusText != null -> {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun WebViewScreen(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Text(text = "← Start over")
            }
            Text(
                text = "Submit your argument",
                style = MaterialTheme.typography.titleMedium
            )
        }

        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    webViewClient = WebViewClient()
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    loadUrl("https://daqtpi.com/referee")
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
