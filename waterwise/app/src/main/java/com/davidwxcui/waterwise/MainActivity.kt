package com.davidwxcui.waterwise

import android.content.Intent
import android.os.Bundle
import android.content.IntentFilter
import android.content.BroadcastReceiver
import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.davidwxcui.waterwise.data.OnboardingPreferences
import com.davidwxcui.waterwise.databinding.ActivityMainBinding
import com.davidwxcui.waterwise.ui.onboarding.OnboardingActivity
import com.davidwxcui.waterwise.ui.profile.FirebaseAuthRepository
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.davidwxcui.waterwise.notifications.EventReminderWorker
import android.util.Log

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val NOTIFICATION_PERMISSION_CODE = 101

    // Broadcast receiver to detect time changes
    private val timeChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_TIME_CHANGED ||
                intent?.action == Intent.ACTION_TIMEZONE_CHANGED) {
                Log.d("MainActivity", "⏰ Device time changed! Rescheduling worker...")
                // Reschedule the worker when time changes
                EventReminderWorker.scheduleEventReminders(this@MainActivity)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request notification permission (Android 13+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_CODE
                )
            }
        }

        // Schedule event reminders
        EventReminderWorker.scheduleEventReminders(this)

        // Register broadcast receiver for time changes
        val intentFilter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        registerReceiver(timeChangeReceiver, intentFilter, Context.RECEIVER_EXPORTED)

        // Check if user is logged in AND onboarding is not completed
        val onboardingPrefs = OnboardingPreferences(this)
        val isLoggedIn = FirebaseAuthRepository.isLoggedIn()

        if (isLoggedIn && !onboardingPrefs.isOnboardingCompleted()) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        supportActionBar?.hide()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
        val navController = navHostFragment.navController

        binding.navView.setupWithNavController(navController)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.navigation_home,
                R.id.friendsFragment,
                R.id.friendRequestsFragment -> {
                    binding.navView.menu.findItem(R.id.navigation_home).isChecked = true
                }
            }
        }

        binding.fabAdd.setOnClickListener {
            for (i in 0 until binding.navView.menu.size()) {
                binding.navView.menu.getItem(i).isChecked = false
            }

            binding.fabAdd.setBackgroundColor(
                android.graphics.Color.parseColor("#00ACC1")
            )

            binding.fabAdd.animate().apply {
                scaleX(1.2f)
                scaleY(1.2f)
                duration = 100
                withEndAction {
                    binding.fabAdd.animate().apply {
                        scaleX(1f)
                        scaleY(1f)
                        duration = 100
                    }.start()
                }
            }.start()

            navController.navigate(R.id.action_global_to_add)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Unregister broadcast receiver
        unregisterReceiver(timeChangeReceiver)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == NOTIFICATION_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d("MainActivity", "✅ Notification permission granted")
            }
        }
    }
}