package com.bleb.bridge.data.repository

import com.bleb.bridge.ble.model.HeartRateSample
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HeartRateRepository @Inject constructor() {

    private val _latestSample = MutableStateFlow<HeartRateSample?>(null)
    val latestSample: StateFlow<HeartRateSample?> = _latestSample.asStateFlow()

    fun updateSample(sample: HeartRateSample) {
        android.util.Log.d("BLEB:HRRepo", "updateSample: ${sample.bpm} bpm from ${sample.source}")
        _latestSample.value = sample
    }

    fun clear() {
        _latestSample.value = null
    }
}
