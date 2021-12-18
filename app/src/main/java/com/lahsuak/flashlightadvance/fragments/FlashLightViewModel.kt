package com.lahsuak.flashlightadvance.fragments

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class FlashLightViewModel: ViewModel() {
    private val lightData:MutableLiveData<Boolean>
    private val runningLightData:MutableLiveData<Boolean>
    private val sliderData:MutableLiveData<Float>
    private val progressData:MutableLiveData<Int>
    private val timeData:MutableLiveData<Int>
    init {
        lightData = MutableLiveData()
        runningLightData = MutableLiveData()
        sliderData = MutableLiveData()
        progressData = MutableLiveData()
        timeData = MutableLiveData()
        lightData.value = false
        runningLightData.value = false
        sliderData.value = 0F
        progressData.value = 0
        timeData.value = 0
    }
    fun setLightData(state: Boolean){
        lightData.value = state
    }
    fun setRunningData(state: Boolean){
        runningLightData.value = state
    }
    fun getLightData(): LiveData<Boolean> {
        return lightData
    }
    fun getSliderProgress():LiveData<Float>{
        return sliderData
    }
    fun getTimeProgress():LiveData<Int>{
        return progressData
    }
    fun getTimeData():LiveData<Int>{
        return timeData
    }
    fun getRunningData():LiveData<Boolean>{
        return runningLightData
    }
}