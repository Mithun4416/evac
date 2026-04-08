package com.evac.app

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.evac.app.databinding.ActivityMainBinding
import com.evac.app.db.AppDatabase
import com.evac.app.db.MessageEntity
import com.evac.app.mesh.MeshService
import com.evac.app.util.DeviceFingerprint
import com.evac.app.util.EvacPowerManager
import com.evac.app.util.VolumeSosDetector
import com.evac.app.util.VolumeUpDetector
import com.evac.app.util.AcousticBeacon
import kotlinx.coroutines.launch
import java.util.UUID
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.KeyEvent
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var database: AppDatabase
    private lateinit var volumeSosDetector: VolumeSosDetector
    private lateinit var volumeUpDetector: VolumeUpDetector


    private fun startMeshService() {
        android.util.Log.i("MainActivity", "Starting MeshService with ACTION_START_MESH")
        val intent = Intent(this, MeshService::class.java).apply {
            action = "ACTION_START_MESH"
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val denied = permissions.filterValues { !it }.keys
        if (denied.isNotEmpty()) {
            android.widget.Toast.makeText(
                this,
                "Mesh network disabled. Missing permissions: ${denied.size}",
                android.widget.Toast.LENGTH_LONG
            ).show()
        } else {
            // All granted!
            android.util.Log.i("MainActivity", "All mesh permissions granted")
            startMeshService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        database = AppDatabase.getInstance(this)



        // Volume SOS detector
        volumeSosDetector = VolumeSosDetector {
            triggerVolumeSos()
        }
        
        // Acoustic Beacon detector
        volumeUpDetector = VolumeUpDetector {
            AcousticBeacon.toggle(this)
        }
        
        if (intent?.action == AcousticBeacon.ACTION_STOP_BEACON) {
            AcousticBeacon.stop(this)
        }

        // Setup bottom nav
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        
        binding.bottomNav.setupWithNavController(navController)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            binding.bottomNav.visibility = if (destination.id == R.id.responderDashboardFragment) {
                android.view.View.GONE
            } else {
                android.view.View.VISIBLE
            }
        }

        requestMeshPermissions()
    }

    private fun requestMeshPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        // Android 12+ (API 31) Bluetooth permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
        }

        // Android 13+ (API 33) Nearby WiFi Devices
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val notGranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (notGranted.isNotEmpty()) {
            permissionLauncher.launch(notGranted.toTypedArray())
        } else {
            startMeshService()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            volumeSosDetector.onVolumeDown()
            // Let system handle volume change normally
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            volumeUpDetector.onVolumeUp()
            // Let system handle volume change normally
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent?.action == AcousticBeacon.ACTION_STOP_BEACON) {
            AcousticBeacon.stop(this)
        }
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
            val deviceId = DeviceFingerprint.getId(this@MainActivity)
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


}