package com.mobisec.omniip.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.android.billingclient.api.*
import com.mobisec.omniip.core.NativeEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class BillingManager(private val context: Context, private val coroutineScope: CoroutineScope) {
    companion object {
        const val TAG = "BillingManager"
        const val SKU_PERSONAL_TIER = "omni_ip_personal_tier"
        const val SKU_ENTERPRISE_TIER = "omni_ip_enterprise_tier"

        const val PREFS_NAME = "premium_entitlement"
        const val KEY_IS_PREMIUM = "is_premium"
        const val KEY_IS_ENTERPRISE = "is_enterprise"
    }

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPrefs = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val _isPersonalUnlocked = MutableStateFlow(sharedPrefs.getBoolean(KEY_IS_PREMIUM, false))
    val isPersonalUnlocked: StateFlow<Boolean> = _isPersonalUnlocked

    private val _isEnterpriseUnlocked = MutableStateFlow(sharedPrefs.getBoolean(KEY_IS_ENTERPRISE, false))
    val isEnterpriseUnlocked: StateFlow<Boolean> = _isEnterpriseUnlocked

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                handlePurchase(purchase)
            }
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            Log.i(TAG, "User canceled purchase")
        } else {
            Log.e(TAG, "Billing error: ${billingResult.debugMessage}")
        }
    }

    private val billingClient = BillingClient.newBuilder(context)
        .setListener(purchasesUpdatedListener)
        .enablePendingPurchases()
        .build()

    private var productDetailsList: List<ProductDetails> = emptyList()

    init {
        if (sharedPrefs.getBoolean(KEY_IS_PREMIUM, false) || sharedPrefs.getBoolean(KEY_IS_ENTERPRISE, false)) {
            NativeEngine.setPremiumUnlockedNative(true)
        }
        connectToBillingService()
    }

    private fun connectToBillingService() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.i(TAG, "Billing client connected")
                    queryProductDetails()
                    queryPurchases() // Restore purchases
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "Billing service disconnected, retrying...")
                connectToBillingService()
            }
        })
    }

    private fun queryProductDetails() {
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(SKU_PERSONAL_TIER)
                .setProductType(BillingClient.ProductType.INAPP)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(SKU_ENTERPRISE_TIER)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )

        val params = QueryProductDetailsParams.newBuilder().setProductList(productList).build()

        billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                this.productDetailsList = productDetailsList
            } else {
                Log.e(TAG, "Failed to query product details: ${billingResult.debugMessage}")
            }
        }
    }

    fun launchBillingFlow(activity: Activity, skuId: String) {
        val productDetails = productDetailsList.find { it.productId == skuId }
        if (productDetails != null) {
            val productDetailsParamsList = listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(productDetails)
                    .build()
            )

            val billingFlowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(productDetailsParamsList)
                .build()

            billingClient.launchBillingFlow(activity, billingFlowParams)
        } else {
            Log.e(TAG, "Product details not found for sku: $skuId")
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            coroutineScope.launch(Dispatchers.IO) {
                if (!purchase.isAcknowledged) {
                    val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build()
                    billingClient.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
                        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                            updateEntitlement(purchase.products)
                        }
                    }
                } else {
                    updateEntitlement(purchase.products)
                }
            }
        }
    }

    private fun updateEntitlement(products: List<String>) {
        val editor = sharedPrefs.edit()
        var hasChanges = false
        if (products.contains(SKU_PERSONAL_TIER)) {
            editor.putBoolean(KEY_IS_PREMIUM, true)
            _isPersonalUnlocked.value = true
            hasChanges = true
        }
        if (products.contains(SKU_ENTERPRISE_TIER)) {
            editor.putBoolean(KEY_IS_ENTERPRISE, true)
            _isEnterpriseUnlocked.value = true
            hasChanges = true
        }
        if (hasChanges) {
            editor.apply()
            NativeEngine.setPremiumUnlockedNative(true)
        }
    }

    fun queryPurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                purchases.forEach { purchase ->
                    handlePurchase(purchase)
                }
            } else {
                Log.e(TAG, "Failed to query purchases: ${billingResult.debugMessage}")
            }
        }
    }
}
