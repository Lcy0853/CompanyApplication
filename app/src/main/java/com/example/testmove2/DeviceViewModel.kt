package com.example.testmove2

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class DeviceViewModel: ViewModel() {
    val connectionStatus = MutableLiveData<String>()

    val isFlashOn = MutableLiveData<Boolean>()

    init {
        connectionStatus.value = "카메라 연결 대기"
        isFlashOn.value = false
    }

    fun ConnectDevice() {
        connectionStatus.value = "카메라 연결 완료"
    }

    fun DisconnectDevice() {
        connectionStatus.value = "카메라 연결 해제"
        isFlashOn.value = false
    }

    fun toggleFlashLight() {
        val currentState =  isFlashOn.value ?: false
        val nextState = !currentState

        isFlashOn.value = nextState

        if(nextState == true) {
            connectionStatus.value = "조명(LED)가 켜졌습니다."
        } else {
            connectionStatus.value = "조명(LED)가 꺼졌습니다."
        }
    }
}