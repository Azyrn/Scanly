package com.skeler.scanely.core.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.skeler.scanely.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "RewardedAdManager"

// AdMob rewarded ads expire after 1h; refresh before that.
private const val AD_REFRESH_MS = 55 * 60 * 1000L

// Reward only via OnUserEarnedRewardListener — never on tap. No post-dismiss chain (AdMob policy).
@Singleton
class RewardedAdManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _isAdAvailable = MutableStateFlow(false)
    val isAdAvailable: StateFlow<Boolean> = _isAdAvailable.asStateFlow()

    private var rewardedAd: RewardedAd? = null
    private var isLoading = false
    private var refreshJob: Job? = null

    init {
        scope.launch(Dispatchers.IO) {
            MobileAds.initialize(context) {}
            launch(Dispatchers.Main) { preload() }
        }
    }

    fun preload() {
        if (isLoading || rewardedAd != null) return
        isLoading = true
        RewardedAd.load(
            context,
            BuildConfig.ADMOB_REWARDED_AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    isLoading = false
                    rewardedAd = ad
                    _isAdAvailable.value = true
                    scheduleRefresh()
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    isLoading = false
                    rewardedAd = null
                    _isAdAvailable.value = false
                    Log.w(TAG, "Rewarded ad failed to load: code=${error.code}")
                }
            }
        )
    }

    private fun scheduleRefresh() {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            delay(AD_REFRESH_MS)
            rewardedAd = null
            _isAdAvailable.value = false
            preload()
        }
    }

    fun show(activity: Activity, onReward: () -> Unit) {
        val ad = rewardedAd ?: run {
            preload()
            return
        }
        rewardedAd = null
        _isAdAvailable.value = false
        refreshJob?.cancel()

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                preload()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.w(TAG, "Rewarded ad failed to show: code=${error.code}")
                preload()
            }
        }
        ad.show(activity) { onReward() }
    }
}
