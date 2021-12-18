package com.lahsuak.flashlightadvance

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.lahsuak.flashlightadvance.util.*
import com.lahsuak.flashlightadvance.util.BIG_FLASH_AS_SWITCH
import com.lahsuak.flashlightadvance.util.FLASH_ON_START
import com.lahsuak.flashlightadvance.util.HAPTIC_FEEDBACK
import com.lahsuak.flashlightadvance.util.SETTING_DATA
import com.lahsuak.flashlightadvance.util.SOS_NUMBER
import com.lahsuak.flashlightadvance.util.TOUCH_SOUND
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import java.text.NumberFormat
import kotlin.math.roundToInt
import android.graphics.SurfaceTexture
import android.hardware.*
import android.util.Log
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.lahsuak.flashlightadvance.fragments.FlashLightViewModel
import kotlinx.coroutines.launch
import java.io.IOException
import kotlin.math.pow
import kotlin.math.sqrt
import android.net.ConnectivityManager
import android.telephony.PhoneStateListener

import android.telephony.TelephonyManager
import android.content.Intent

import android.content.BroadcastReceiver


private const val TAG = "MainActivity"

class MainActivity : AppCompatActivity(), SensorEventListener {
    private val SHAKE_THRESOLD = 3.25f
    private val MIN_TIME_BETWEEN_SHAKES_MILLIsECS = 1000
    private var mLastShakeTime: Long = 0
    private lateinit var sensorManager: SensorManager
    private lateinit var flashBtn: ImageView
    private lateinit var playBtn: ImageButton
    private lateinit var sosBtn: ImageButton
    private lateinit var progressBar: ProgressBar
    private lateinit var timePicker: Spinner

    //extra
    private var job: Job? = null
    private lateinit var phoneStateReceiver: CallReceiver

    //SOS settings
    private lateinit var bottomSheetDialog: BottomSheetDialog
    private lateinit var firstUser: TextInputEditText
    private lateinit var hapticFeedbackSwitch: SwitchMaterial
    private lateinit var touchSoundSwitch: SwitchMaterial
    private lateinit var flashOnAtStartSwitch: SwitchMaterial
    private lateinit var bigFlashSwitch: SwitchMaterial
    private lateinit var shakeToLight: SwitchMaterial

    private var state = false
    private var onOrOff = false
    private var isRunning = false
    private var time = 1
    private var isHapticFeedBackEnable = true
    private var isSoundEnable = false
    private var flashOnAtStartUpEnable = false
    private var bigFlashAsSwitchEnable = false
    private var shakeToLightEnable = false

    private lateinit var viewModel: FlashLightViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        flashBtn = findViewById(R.id.torchBtn)
        val rangeSlider = findViewById<Slider>(R.id.lightSlider)
        playBtn = findViewById(R.id.playBtn)
        sosBtn = findViewById(R.id.sosBtn)
        progressBar = findViewById(R.id.progress_circular)
        timePicker = findViewById(R.id.timePicker)

        phoneStateReceiver = CallReceiver()
        checkPhoneStatePermission()
        val filter = IntentFilter(TelephonyManager.EXTRA_STATE)
        registerReceiver(phoneStateReceiver, filter)

        val broadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent) {
                val b = intent.extras
                val isState = b!!.getInt("message")
//                if(isState == 0)
//                {
//                    switchingFlash(100)
//                }else if(isState!=0){
//
//                }
                Log.d("TAG", "in activity" + isState)
            }
        }
        registerReceiver(broadcastReceiver, filter)

        //this is for SOS settings
        bottomSheetDialog = BottomSheetDialog(this)
        bottomSheetDialog.setContentView(R.layout.fragment_setting)
        firstUser = bottomSheetDialog.findViewById(R.id.first_number)!!
        val addContactBtn = bottomSheetDialog.findViewById<Button>(R.id.addBtn)!!
        val cancelBtn = bottomSheetDialog.findViewById<Button>(R.id.cancelBtn)!!
        hapticFeedbackSwitch = bottomSheetDialog.findViewById(R.id.haptic_feedback)!!
        touchSoundSwitch = bottomSheetDialog.findViewById(R.id.sound_feedback)!!
        flashOnAtStartSwitch = bottomSheetDialog.findViewById(R.id.start_flash)!!
        bigFlashSwitch = bottomSheetDialog.findViewById(R.id.big_flash_btn)!!
        shakeToLight = bottomSheetDialog.findViewById(R.id.shake_to_light)!!

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager

        viewModel = ViewModelProvider(this).get(FlashLightViewModel::class.java)

        //Shake to turn ON/OFF flashlight
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (sensor != null) {
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }

        viewModel.getLightData().observe(this, { isLightOn ->
            state = isLightOn
            if (state)
                turnFlash(true)
            else
                turnFlash(false)
        })
        viewModel.getLightData().observe(this, { isRun ->
            isRunning = isRun
        })

//        if (savedInstanceState != null) {
//            state = savedInstanceState.getBoolean("isFlashOn", false)
//            if (state)
//                turnFlash(true)
//            else
//                turnFlash(false)
//        }

        //Flash light fragment methods
        //flashlight blinking time
        setTime()
        checkSwitch()

        if (flashOnAtStartUpEnable) {
            turnFlash(true)
        }

        sosBtn.setOnClickListener {
            if (isSoundEnable) {
                playSound()
            }
            if (isHapticFeedBackEnable) {
                hapticFeedback(sosBtn)
            }
            switchingFlash(100)
            checkPermission()
        }
        flashBtn.setOnClickListener {
            if (bigFlashAsSwitchEnable) {
                startFlash()
            }
        }
        playBtn.setOnClickListener {
            startFlash()
        }

        rangeSlider.addOnSliderTouchListener(
            object : Slider.OnSliderTouchListener {
                override fun onStartTrackingTouch(slider: Slider) {
                }

                override fun onStopTrackingTouch(slider: Slider) {
                    if (isHapticFeedBackEnable) {
                        hapticFeedback(rangeSlider)
                    }
                    if (isSoundEnable) {
                        playSound()
                    }
                    if (isRunning) {
                        lifecycleScope.launch {
                            onOrOff = true
                            delay(500)
                            if (slider.value.roundToInt() > 0) {
                                switchingFlash(slider.value.roundToInt())
                            } else if (slider.value.roundToInt() == 0) {
                                turnFlash(true)
                            }
                        }
                    } else {
                        onOrOff = false
                        if (slider.value.roundToInt() > 0) {
                            switchingFlash(slider.value.roundToInt())
                        } else if (slider.value.roundToInt() == 0) {
                            turnFlash(true)
                        }
                    }
                }
            })
        rangeSlider.setLabelFormatter { value: Float ->
            val format = NumberFormat.getInstance()
            format.maximumFractionDigits = 0
            //format.currency = Currency.getInstance("USD")
            format.format(value.toDouble() / 10)
        }

        //Settings methods

        hapticFeedbackSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (!isChecked) {
                hapticFeedbackSwitch.isChecked = false
                hapticFeedbackSwitch.text = getString(R.string.haptic_feedback_disable)
                isHapticFeedBackEnable = false
            } else {
                hapticFeedbackSwitch.isChecked = true
                hapticFeedbackSwitch.text = getString(R.string.haptic_feedback_enable)
                isHapticFeedBackEnable = true
            }
        }
        touchSoundSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (!isChecked) {
                touchSoundSwitch.isChecked = false
                touchSoundSwitch.text = getString(R.string.touch_sound_disable)
                isSoundEnable = false
            } else {
                touchSoundSwitch.isChecked = true
                touchSoundSwitch.text = getString(R.string.touch_sound_enable)
                isSoundEnable = true
            }
        }
        flashOnAtStartSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (!isChecked) {
                flashOnAtStartSwitch.isChecked = false
                flashOnAtStartSwitch.text = getString(R.string.flash_off_at_start)
                flashOnAtStartUpEnable = false
            } else {
                flashOnAtStartSwitch.isChecked = true
                flashOnAtStartSwitch.text = getString(R.string.flash_on_at_start)
                flashOnAtStartUpEnable = true
            }
        }
        bigFlashSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (!isChecked) {
                bigFlashSwitch.isChecked = false
                bigFlashSwitch.text = getString(R.string.big_flash_disable)
                bigFlashAsSwitchEnable = false
            } else {
                bigFlashSwitch.isChecked = true
                bigFlashSwitch.text = getString(R.string.big_flash_enable)
                bigFlashAsSwitchEnable = true
            }
        }
        shakeToLight.setOnCheckedChangeListener { _, isChecked ->
            if (!isChecked) {
                shakeToLight.isChecked = false
                shakeToLight.text = getString(R.string.shake_to_light_disable)
                shakeToLightEnable = false
            } else {
                shakeToLight.isChecked = true
                shakeToLight.text = getString(R.string.shake_to_light_enable)
                shakeToLightEnable = true
            }
        }
        addContactBtn.setOnClickListener {
            val preference = getSharedPreferences(SETTING_DATA, MODE_PRIVATE)
            val sosNo = preference.getString(SOS_NUMBER, null)

            if (!firstUser.text.isNullOrEmpty()) {
                saveSetting()
                if (sosNo != firstUser.text.toString())
                    notifyUser(this, "Contact is successfully added")
                bottomSheetDialog.dismiss()
            } else
                notifyUser(this, "Please fill up all details")
        }
        cancelBtn.setOnClickListener {
            bottomSheetDialog.dismiss()
        }

    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.app_menu, menu)
        menu?.getItem(4)?.title = "Current Version ${BuildConfig.VERSION_NAME}"
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.app_manager -> {
                bottomSheetDialog.show()
            }
            R.id.more_apps -> {
                moreApp()
            }
            R.id.share_app -> {
                shareApp()
            }
            R.id.feedback -> {
                sendFeedbackMail()
            }
            // R.id.version ->item.title = "Current Version ${BuildConfig.VERSION_NAME}"
        }
        return super.onOptionsItemSelected(item)
    }

    private fun switchingFlash(noOfTimes: Int) {
        isRunning = true
        onOrOff = false
        var flashOn = true
        progressBar.max = time * 60
        playBtn.setImageResource(R.drawable.ic_pause)
        turnFlash(flashOn)
        var time1 = 0.0
        job = lifecycleScope.launch {
            for (count in 0 until time * 60 * noOfTimes) {
                if (time1 >= time * 60.0) {
                    turnFlash(false)
                    isRunning = false
                    break
                }
                val time2: Double =
                    60 / (time * noOfTimes * 6).toDouble()  //this is for slider progress

                flashOn = !flashOn
                val delayTime = 10000 / noOfTimes
                if (noOfTimes != 100) {
                    time1 += time2 + noOfTimes / 100
                }
                progressBar.progress = time1.toInt()//count/time1.toInt()

                delay(delayTime.toLong())

                turnFlash(flashOn)
                //check whether user pause the timer or not
                if (onOrOff) {
                    turnFlash(false)
                    onOrOff = false
                    break
                }
            }
        }
    }

    private fun turnFlash(isCheck: Boolean) {
        val isFlashAvailableOnDevice =
            packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
        if (!isFlashAvailableOnDevice) {
            notifyUser(this, "Your device doesn't support flash light")
        } else {
            val cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
            try {
                val cameraId = cameraManager.cameraIdList[0]
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    cameraManager.setTorchMode(cameraId, isCheck)
                } else {
                    //cameraManager.setTorchMode(cameraId,isChek)
                    val mCam = Camera.open()
                    val p: Camera.Parameters = mCam.parameters
                    p.flashMode = Camera.Parameters.FLASH_MODE_TORCH
                    mCam.parameters = p
                    val mPreviewTexture = SurfaceTexture(0)
                    try {
                        mCam.setPreviewTexture(mPreviewTexture)
                    } catch (ex: IOException) {
                        // Ignore
                    }
                    mCam.startPreview()
                }
                state = isCheck
                if (isCheck) {
                    flashBtn.setImageResource(R.drawable.light_bulb)
                    // if (!isRunning)
                    playBtn.setImageResource(R.drawable.ic_pause)
                } else {
                    flashBtn.setImageResource(R.drawable.light_bulb_off)
                    // if (!isRunning)
                    playBtn.setImageResource(R.drawable.ic_play)
                }
            } catch (e: CameraAccessException) {
            }
        }
    }

    private fun runFlashlight() {
        progressBar.progress = 0
        if (!state) {
            turnFlash(true)
        } else {
            turnFlash(false)
        }

    }

    private fun startFlash() {
        if (isSoundEnable) {
            playSound()
        }
        if (isHapticFeedBackEnable) {
            hapticFeedback(playBtn)
        }
        runFlashlight()
        if (isRunning) {
            if (onOrOff) {
                onOrOff = false
                //playBtn.setImageResource(R.drawable.ic_pause)
            } else {
                onOrOff = true
                playBtn.setImageResource(R.drawable.ic_play)
            }
            isRunning = false
            job?.cancel()
            turnFlash(false)
            playBtn.setImageResource(R.drawable.ic_play)
        }
    }

    private fun checkSwitch() {
        //check haptic feedback is ON or OFF and set haptic feedback option item according to this value
        val preference = getSharedPreferences(SETTING_DATA, MODE_PRIVATE)
        isHapticFeedBackEnable = preference.getBoolean(HAPTIC_FEEDBACK, false)
        isSoundEnable = preference.getBoolean(TOUCH_SOUND, false)
        flashOnAtStartUpEnable = preference.getBoolean(FLASH_ON_START, false)
        bigFlashAsSwitchEnable = preference.getBoolean(BIG_FLASH_AS_SWITCH, false)
        shakeToLightEnable = preference.getBoolean(SHAKE_TO_LIGHT, false)
        val sosNo = preference.getString(SOS_NUMBER, null)
        firstUser.setText(sosNo)

        if (isHapticFeedBackEnable) {
            hapticFeedbackSwitch.isChecked = true
            hapticFeedbackSwitch.text = getString(R.string.haptic_feedback_enable)
        } else {
            hapticFeedbackSwitch.isChecked = false
            hapticFeedbackSwitch.text = getString(R.string.haptic_feedback_disable)
        }
        if (isSoundEnable) {
            touchSoundSwitch.isChecked = true
            touchSoundSwitch.text = getString(R.string.touch_sound_enable)
        } else {
            touchSoundSwitch.isChecked = false
            touchSoundSwitch.text = getString(R.string.touch_sound_disable)
        }
        if (flashOnAtStartUpEnable) {
            flashOnAtStartSwitch.isChecked = true
            flashOnAtStartSwitch.text = getString(R.string.flash_on_at_start)
        } else {
            flashOnAtStartSwitch.isChecked = false
            flashOnAtStartSwitch.text = getString(R.string.flash_off_at_start)
        }
        if (bigFlashAsSwitchEnable) {
            bigFlashSwitch.isChecked = true
            bigFlashSwitch.text = getString(R.string.big_flash_enable)
        } else {
            bigFlashSwitch.isChecked = false
            bigFlashSwitch.text = getString(R.string.big_flash_disable)
        }
        if (shakeToLightEnable) {
            shakeToLight.isChecked = true
            shakeToLight.text = getString(R.string.shake_to_light_enable)
        } else {
            shakeToLight.isChecked = false
            shakeToLight.text = getString(R.string.shake_to_light_disable)
        }
    }

    private fun checkPhoneStatePermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_PHONE_STATE
            ) == PackageManager.PERMISSION_DENIED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_PHONE_STATE),
                READ_PHONE_STATE_REQUEST_CODE
            )
        }
    }

    private fun checkPermission() {
        // Checking if permission is not granted
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CALL_PHONE
            ) == PackageManager.PERMISSION_DENIED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CALL_PHONE),
                PHONE_REQUEST_CODE
            )
        } else {
            phoneCall()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PHONE_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                phoneCall()
            } else {
                notifyUser(this, "Camera Permission Denied")
            }
        }
        if (requestCode == READ_PHONE_STATE_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                notifyUser(this, "Flash will blinking when call arrived")
            } else {
                notifyUser(this, "Phone state Permission Denied")
            }
        }
    }

    private fun setTime() {
        val sortAdapter: ArrayAdapter<*> = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            resources.getStringArray(R.array.time_list)
        )
        sortAdapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item
        )
        timePicker.adapter = sortAdapter

        var order = 0
        //we need by default selected sort when app start
        timePicker.setSelection(order)

        //pass view:View? for null reference
        timePicker.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {

                override fun onItemSelected(
                    adapterView: AdapterView<*>,
                    view: View?, item: Int, l: Long
                ) {
                    if (order != item) {
                        order = item
                        when (order) {
                            0 -> time = 1
                            1 -> time = 2
                            2 -> time = 5
                            3 -> time = 10
                            4 -> time = 15
                            5 -> time = 30
                            6 -> time = 45
                            7 -> time = 60
//                            0 -> time = 15
//                            1 -> time = 30
//                            2 -> time = 60
//                            3 -> time = 2 * 60
//                            4 -> time = 5 * 60
//                            5 -> time = 10 * 60
//                            6 -> time = 15 * 60
                        }
                    }
                }

                override fun onNothingSelected(adapterView: AdapterView<*>?) {}
            }
    }

    private fun phoneCall() {
        val callIntent = Intent(Intent.ACTION_CALL)
        val pref = getSharedPreferences(SETTING_DATA, MODE_PRIVATE)
        val phNo1 = pref.getString(SOS_NUMBER, null)
        callIntent.data = Uri.parse("tel: $phNo1")
        startActivity(callIntent)
    }

    private fun playSound() {
        val mediaPlayer = MediaPlayer.create(this, R.raw.button_sound)
        mediaPlayer.start()
    }

    private fun hapticFeedback(view: View) {
        view.performHapticFeedback(
            HapticFeedbackConstants.VIRTUAL_KEY,
            HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING // Ignore device's setting. Otherwise, you can use FLAG_IGNORE_VIEW_SETTING to ignore view's setting.
        )
    }

    private fun moreApp() {
        try {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("market://search?q=Kaushal Vasava")
                )
            )
        } catch (e: ActivityNotFoundException) {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("http://play.google.com/store/apps/developer?id=Kaushal Vasava")
                )
            )
        }
    }

    private fun shareApp() {
        try {
            val intent = Intent(Intent.ACTION_SEND)
            intent.type = "text/plain"
            intent.putExtra(Intent.EXTRA_SUBJECT, "Share this App")
            val shareMsg =
                "https://play.google.com/store/apps/details?id=" + BuildConfig.APPLICATION_ID + "\n\n"
            intent.putExtra(Intent.EXTRA_TEXT, shareMsg)
            startActivity(Intent.createChooser(intent, "Share by"))
        } catch (e: Exception) {
            notifyUser(
                this,
                "Some thing went wrong!!"
            )
        }
    }

    private fun sendFeedbackMail() {
        try {
            val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:")
                putExtra(Intent.EXTRA_EMAIL, arrayOf(getString(R.string.feedback_email)))
                putExtra(Intent.EXTRA_TEXT, "Please write your suggestions or issues")
                putExtra(Intent.EXTRA_SUBJECT, "Feedback From FlashLight")
            }
            startActivity(emailIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    //save app settings
    private fun saveSetting() {
        val editor = getSharedPreferences(SETTING_DATA, MODE_PRIVATE).edit()
        editor.putBoolean(HAPTIC_FEEDBACK, isHapticFeedBackEnable)
        editor.putBoolean(TOUCH_SOUND, isSoundEnable)
        editor.putBoolean(FLASH_ON_START, flashOnAtStartUpEnable)
        editor.putBoolean(BIG_FLASH_AS_SWITCH, bigFlashAsSwitchEnable)
        editor.putBoolean(SHAKE_TO_LIGHT, shakeToLightEnable)
        editor.putString(SOS_NUMBER, firstUser.text.toString())
        editor.apply()
    }


    private fun notifyUser(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.setLightData(state)
        viewModel.setRunningData(isRunning)
        sensorManager.unregisterListener(this)
        unregisterReceiver(phoneStateReceiver)
//        viewModel._lightData.value=state
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event != null) {
            if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                val curTime = System.currentTimeMillis()
                if ((curTime - mLastShakeTime) > MIN_TIME_BETWEEN_SHAKES_MILLIsECS) {
                    val x = event.values[0]
                    val y = event.values[1]
                    val z = event.values[2]
                    val acceleration =
                        sqrt(x.pow(2) + y.pow(2) + z.pow(2)) - SensorManager.GRAVITY_EARTH
                    //  Log.d(TAG, "onSensorChanged:Acceleration is $acceleration")
                    if (shakeToLightEnable) {
                        if (acceleration > SHAKE_THRESOLD) {
                            mLastShakeTime = curTime
                            Log.d(TAG, "onSensorChanged: Shake")
                            if (state)
                                turnFlash(false)
                            else
                                turnFlash(true)
                        }
                    }
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    }

//    override fun onIncomingCall(callState: Boolean) {
//        if(callState){
//            switchingFlash(100)
//        }
//    }
//
//    override fun onCallReceived(callState: Boolean) {
//        if(!callState){
//            turnFlash(false)
//        }
//    }
//      class CallReceiver : BroadcastReceiver(){
//        override fun onReceive(context: Context?, intent: Intent?) {
//            val telephoneManager = context?.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
//
//            telephoneManager.listen(object : PhoneStateListener() {
//                override fun onCallStateChanged(state: Int, phoneNumber: String?) {
//                    super.onCallStateChanged(state, phoneNumber)
//                    if(state==1) {
//                        switchingFlash(100)
//                        //listener.onIncomingCall(isCallArrived)
//                    }
//                    else{
//                        turnFlash(false)
//                    }
//                    Log.d("TAG", "onCallStateChanged: incoming call $phoneNumber  and state $state")
//                }
//            }, PhoneStateListener.LISTEN_CALL_STATE)
//        }
//    }

}