package com.intelligame.huntix.battle

import android.app.Activity
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.intelligame.huntix.BuildConfig

object AdsManager {
    private var interstitial: InterstitialAd? = null
    private var rewarded: RewardedAd? = null
    private const val TAG = "AdsManager"

    fun init(activity: Activity) {
        loadInterstitial(activity)
        loadRewarded(activity)
    }

    private fun loadInterstitial(activity: Activity) {
        val id = BuildConfig.ADMOB_INTERSTITIAL_ID
        if (id.isBlank()) { Log.d(TAG, "No interstitial ID configured"); return }
        InterstitialAd.load(activity, id, AdRequest.Builder().build(), object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(ad: InterstitialAd) { interstitial = ad }
            override fun onAdFailedToLoad(error: LoadAdError) { Log.w(TAG, "Interstitial load failed: ${error.message}") }
        })
    }

    private fun loadRewarded(activity: Activity) {
        val id = BuildConfig.ADMOB_REWARDED_ID
        if (id.isBlank()) { Log.d(TAG, "No rewarded ID configured"); return }
        RewardedAd.load(activity, id, AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) { rewarded = ad }
            override fun onAdFailedToLoad(error: LoadAdError) { Log.w(TAG, "Rewarded load failed: ${error.message}") }
        })
    }

    fun showInterstitial(activity: Activity, onDismissed: (() -> Unit)? = null) {
        val ad = interstitial ?: run { onDismissed?.invoke(); return }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() { interstitial = null; onDismissed?.invoke(); loadInterstitial(activity) }
            override fun onAdFailedToShowFullScreenContent(error: AdError) { onDismissed?.invoke() }
        }
        ad.show(activity)
    }

    fun showRewarded(activity: Activity, onRewarded: () -> Unit, onDismissed: (() -> Unit)? = null) {
        val ad = rewarded ?: run { onDismissed?.invoke(); return }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() { rewarded = null; onDismissed?.invoke(); loadRewarded(activity) }
            override fun onAdFailedToShowFullScreenContent(error: AdError) { onDismissed?.invoke() }
        }
        ad.show(activity) { onRewarded() }
    }

    fun onBattleCompleted(activity: Activity) {
        showInterstitial(activity)
    }
}
