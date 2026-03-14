package com.evac.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.KeyEvent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.evac.app.databinding.ActivityMainBinding
import com.evac.app.db.AppDatabase
import com.evac.app.db.MessageEntity
import com.evac.app.mesh.MeshService
import com.evac.app.util.DeviceFingerprint
import com.evac.app.util.EvacPowerManager
import com.evac.app.util.VolumeSosDetector
import kotlinx.coroutines.launch
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var volumeSosDetector: VolumeSosDetector
    private lateinit var database: AppDatabase

    private val PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.POST_NOTIFICATIONS
        )
    } else {
        arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        database = AppDatabase.getDatabase(this)

        // Request permissions
        requestPermissions()

        // Volume SOS detector
        volumeSosDetector = VolumeSosDetector {
            triggerVolumeSos()
        }

        // Setup bottom nav
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        binding.bottomNav.setupWithNavController(navController)

        // Start mesh service
        val serviceIntent = Intent(this, MeshService::class.java)
        startForegroundService(serviceIntent)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            volumeSosDetector.onVolumeDown()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun triggerVolumeSos() {
        // Haptic feedback
        val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(300)
        }

        // Save TRAPPED SOS to DB
        lifecycleScope.launch {
            val deviceId = DeviceFingerprint.getDeviceId(this@MainActivity)
            val batteryPct = EvacPowerManager.getBatteryPct(this@MainActivity)

            val message = MessageEntity(
                id = UUID.randomUUID().toString(),
                type = "SOS",
                status = "TRAPPED",
                deviceId = deviceId,
                timestamp = System.currentTimeMillis(),
                ttlHours = 24,
                hopCount = 0,
                maxHops = 10,
                lat = null,
                lng = null,
                accuracyM = null,
                peopleCount = 1,
                batteryPct = batteryPct,
                note = "Volume SOS",
                phraseKey = null,
                isVolumeSos = true,
                hash = "temp",
                body = null,
                targetDeviceId = null,
                signature = null
            )
            database.messageDao().insert(message)
        }
    }

    private fun requestPermissions() {
        val missing = PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 101)
        }
    }
}