package com.lahsuak.flashlightadvance.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.lahsuak.flashlightadvance.PhoneListener
import java.io.IOException
import android.content.Intent.getIntent





class CallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val telephoneManager =
            context?.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        telephoneManager.listen(object : PhoneStateListener() {
            override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                super.onCallStateChanged(state, phoneNumber)
                //state 0 = rejected
                //state 1 = incoming call
                //state 2 = received
//                val i = Intent("CallReceiver")
//                i.putExtra("message", state)
//                context.sendBroadcast(i)
                val intent1 = Intent()
                intent1.putExtra("message",state)
                context.startActivity(intent1)
                Log.d("TAG", "onCallStateChanged: incoming call $phoneNumber  and state $state")
            }
        }, PhoneStateListener.LISTEN_CALL_STATE)
    }
}