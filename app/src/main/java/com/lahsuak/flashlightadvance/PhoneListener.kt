package com.lahsuak.flashlightadvance

interface PhoneListener {
    fun onIncomingCall(callState: Boolean)
    fun onCallReceived(callState: Boolean)
}