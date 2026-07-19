package com.intelligame.huntix

import android.os.Handler
import android.os.Looper
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.OnUserEarnedRewardListener
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

// ── AdMob ───────────────────────────────────────────────────────
internal fun MainActivity.initAdMob() {
    if (adsInitialized) return
    adsInitialized = true

    MobileAds.initialize(this) {
        loadRewardedAd()
        loadBannerAd()
    }
}

internal fun MainActivity.loadBannerAd() {
    binding.adBannerContainer.removeAllViews()

    val adView = AdView(this).apply {
        adUnitId = MainActivity.ADMOB_BANNER_ID
        setAdSize(AdSize.BANNER)
    }

    binding.adBannerContainer.addView(adView)
    adView.loadAd(AdRequest.Builder().build())
}

internal fun MainActivity.loadRewardedAd() {
    RewardedAd.load(
        this,
        MainActivity.ADMOB_REWARDED_ID,
        AdRequest.Builder().build(),
        object : RewardedAdLoadCallback() {

            override fun onAdLoaded(ad: RewardedAd) {
                rewardedAd = ad
                rewardedAd?.fullScreenContentCallback = object : FullScreenContentCallback() {

                    override fun onAdDismissedFullScreenContent() {
                        rewardedAd = null
                        loadRewardedAd()
                    }

                    override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                        rewardedAd = null
                        loadRewardedAd()
                    }
                }
            }

            override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                rewardedAd = null
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ loadRewardedAd() }, 130_000)
            }
        }
    )
}
