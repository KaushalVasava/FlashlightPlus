package com.lahsuak.flashlightadvance.fragments

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.media.MediaPlayer
import android.net.Uri
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.slider.Slider
import com.lahsuak.flashlightadvance.R
import com.lahsuak.flashlightadvance.BuildConfig
import java.text.NumberFormat
import android.os.*
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.textfield.TextInputEditText

import androidx.core.app.ActivityCompat
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.widget.Toast
import android.os.Build
import android.util.Log
import com.google.android.material.switchmaterial.SwitchMaterial
import com.lahsuak.flashlightadvance.util.*
import kotlinx.coroutines.*
import kotlin.math.roundToInt

private const val TAG = "FlashLightFragment"

class FlashLightFragment : Fragment(R.layout.fragment_flash_light) {
    private lateinit var flashBtn: ImageView
    private lateinit var playBtn: ImageButton
    private lateinit var sosBtn: ImageButton
    private lateinit var progressBar: ProgressBar
    private lateinit var timePicker: Spinner
    private var job: Job? = null

    //SOS settings
    private lateinit var bottomSheetDialog: BottomSheetDialog
    private lateinit var firstUser: TextInputEditText
    private lateinit var hapticFeedbackSwitch: SwitchMaterial
    private lateinit var touchSoundSwitch: SwitchMaterial
    private lateinit var flashOnAtStartSwitch: SwitchMaterial
    private lateinit var bigFlashSwitch: SwitchMaterial

    private var state = false
    private var onOrOff = false
    private var isRunning = false
    private var time = 1
    private var isHapticFeedBackEnable = true
    private var isSoundEnable = false
    private var flashOnAtStartUpEnable = false
    private var bigFlashAsSwitchEnable = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        setHasOptionsMenu(true)
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        flashBtn = view.findViewById(R.id.torchBtn)
        val rangeSlider = view.findViewById<Slider>(R.id.lightSlider)
        playBtn = view.findViewById(R.id.playBtn)
        sosBtn = view.findViewById(R.id.sosBtn)
        progressBar = view.findViewById(R.id.progress_circular)
        timePicker = view.findViewById(R.id.timePicker)


        //this is for SOS settings
        bottomSheetDialog = BottomSheetDialog(requireContext())
        bottomSheetDialog.setContentView(R.layout.fragment_sos_setting)
        firstUser = bottomSheetDialog.findViewById(R.id.first_number)!!
        val addContactBtn = bottomSheetDialog.findViewById<Button>(R.id.addBtn)!!
        val cancelBtn = bottomSheetDialog.findViewById<Button>(R.id.cancelBtn)!!
        hapticFeedbackSwitch = bottomSheetDialog.findViewById(R.id.haptic_feedback)!!
        touchSoundSwitch = bottomSheetDialog.findViewById(R.id.sound_feedback)!!
        flashOnAtStartSwitch = bottomSheetDialog.findViewById(R.id.start_flash)!!
        bigFlashSwitch = bottomSheetDialog.findViewById(R.id.big_flash_btn)!!

        if (savedInstanceState != null) {
            state = savedInstanceState.getBoolean("isFlashOn", false)
            if (state)
                turnFlash(true)
            else
                turnFlash(false)
        }

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
                        viewLifecycleOwner.lifecycleScope.launch {
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
        addContactBtn.setOnClickListener {
            if (!firstUser.text.isNullOrEmpty()) {
                //&& !secondUser.text.isNullOrEmpty() && !thirdUser.text.isNullOrEmpty()) {
                saveSetting()
                notifyUser(requireContext(), "Contact is successfully added")
                bottomSheetDialog.dismiss()
            } else
                notifyUser(requireContext(), "Please fill up all details")
        }
        cancelBtn.setOnClickListener {
            bottomSheetDialog.dismiss()
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
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
        val preference = requireActivity().getSharedPreferences(SETTING_DATA, MODE_PRIVATE)
        isHapticFeedBackEnable = preference.getBoolean(HAPTIC_FEEDBACK, false)
        isSoundEnable = preference.getBoolean(TOUCH_SOUND, false)
        flashOnAtStartUpEnable = preference.getBoolean(FLASH_ON_START, false)
        bigFlashAsSwitchEnable = preference.getBoolean(BIG_FLASH_AS_SWITCH,false)
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
    }

    private fun checkPermission() {
        // Checking if permission is not granted
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.CALL_PHONE
            ) == PackageManager.PERMISSION_DENIED
        ) {
            ActivityCompat.requestPermissions(
                requireActivity(),
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
        //    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PHONE_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                phoneCall()
            } else {
                notifyUser(requireContext(), "Camera Permission Denied")
            }
        }
    }

    private fun phoneCall() {
        val callIntent = Intent(Intent.ACTION_CALL)
        val pref = requireActivity().getSharedPreferences(SETTING_DATA, MODE_PRIVATE)
        val phNo1 = pref.getString(SOS_NUMBER, null)
        callIntent.data = Uri.parse("tel: $phNo1")
        startActivity(callIntent)
    }

    private fun playSound() {
        val mediaPlayer = MediaPlayer.create(requireContext(), R.raw.button_sound)
        mediaPlayer.start()
    }

    private fun hapticFeedback(view: View) {
        view.performHapticFeedback(
            HapticFeedbackConstants.VIRTUAL_KEY,
            HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING // Ignore device's setting. Otherwise, you can use FLAG_IGNORE_VIEW_SETTING to ignore view's setting.
        )
    }

    private fun setTime() {
        val sortAdapter: ArrayAdapter<*> = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            requireActivity().resources.getStringArray(R.array.time_list)
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
                        }
                    }
                }

                override fun onNothingSelected(adapterView: AdapterView<*>?) {}
            }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("isFlashOn", state)
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun switchingFlash(noOfTimes: Int) {
        isRunning = true
        onOrOff = false
        var flashOn = true
        progressBar.max = time * 60
        playBtn.setImageResource(R.drawable.ic_pause)
        turnFlash(flashOn)
        var time1 = 0.0
        job = this.viewLifecycleOwner.lifecycleScope.launch {
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
                time1 += time2 + noOfTimes / 100

                Log.d(TAG, "switchingFlash: $time2 and time1 : $time1 and #$count and $noOfTimes")
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

    @RequiresApi(Build.VERSION_CODES.M)
    private fun turnFlash(isChek: Boolean) {
        val cameraManager =
            requireActivity().getSystemService(AppCompatActivity.CAMERA_SERVICE) as CameraManager
        try {
            val cameraId = cameraManager.cameraIdList[0]
            cameraManager.setTorchMode(cameraId, isChek)
            state = isChek
            if (isChek) {
                flashBtn.setImageResource(R.drawable.light_bulb)
                if (!isRunning)
                    playBtn.setImageResource(R.drawable.ic_pause)
            } else {
                flashBtn.setImageResource(R.drawable.light_bulb_off)
                if (!isRunning)
                    playBtn.setImageResource(R.drawable.ic_play)
            }
        } catch (e: CameraAccessException) {
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun runFlashlight() {
        progressBar.progress = 0
        if (!state) {
            turnFlash(true)
        } else {
            turnFlash(false)
        }

    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.app_menu, menu)

        //check haptic feedback is ON or OFF and set haptic feedback option item according to this value
//        val preference = requireActivity().getSharedPreferences("VIEW_FEEDBACK", MODE_PRIVATE)
//        isHapticFeedBackEnable = preference.getBoolean("haptic_feedback", false)
//        isSoundEnable = preference.getBoolean("sound_feedback", false)
//        menu.getItem(1).isChecked = isHapticFeedBackEnable
//        menu.getItem(2).isChecked = isSoundEnable
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.app_manager -> {
                bottomSheetDialog.show()
            }
//            R.id.haptic_feedback -> {
//                if (item.isChecked) {
//                    item.isChecked = false
//                    isHapticFeedBackEnable = false
//                    item.isCheckable = true
//                } else {
//                    item.isChecked = true
//                    isHapticFeedBackEnable = true
//                    item.isCheckable = true
//                }
//            }
//            R.id.sound_feedback -> {
//                if (item.isChecked) {
//                    item.isChecked = false
//                    isSoundEnable = false
//                    item.isCheckable = true
//                } else {
//                    item.isChecked = true
//                    isSoundEnable = true
//                    item.isCheckable = true
//                }
//            }
            R.id.more_apps -> {
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
            R.id.share_app -> {
                shareApp()
            }
            R.id.feedback -> {
                sendFeedbackMail()
            }
            R.id.version -> notifyUser(
                requireContext(),
                "Current Version ${BuildConfig.VERSION_NAME}"
            )
        }
        return super.onOptionsItemSelected(item)
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
        val editor = requireActivity().getSharedPreferences(SETTING_DATA, MODE_PRIVATE).edit()
        editor.putBoolean(HAPTIC_FEEDBACK, isHapticFeedBackEnable)
        editor.putBoolean(TOUCH_SOUND, isSoundEnable)
        editor.putBoolean(FLASH_ON_START, flashOnAtStartUpEnable)
        editor.putBoolean(BIG_FLASH_AS_SWITCH, bigFlashAsSwitchEnable)
        editor.putString(SOS_NUMBER, firstUser.text.toString())
        editor.apply()
    }

    private fun shareApp() {
        try {
            val intent = Intent(Intent.ACTION_SEND)
            intent.type = "text/plain"
            intent.putExtra(Intent.EXTRA_SUBJECT, "Share this App")
            val shareMsg =
                "https://play.google.com/store/apps/details?id=" + BuildConfig.APPLICATION_ID + "\n\n"
            intent.putExtra(Intent.EXTRA_TEXT, shareMsg)
            requireActivity().startActivity(Intent.createChooser(intent, "Share by"))
        } catch (e: Exception) {
            notifyUser(
                requireContext(),
                "Some thing went wrong!!"
            )
        }
    }

    private fun notifyUser(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        saveSetting()
    }
}