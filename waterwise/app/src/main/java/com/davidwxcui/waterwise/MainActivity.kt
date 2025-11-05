package com.davidwxcui.waterwise

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.davidwxcui.waterwise.data.OnboardingPreferences
import com.davidwxcui.waterwise.databinding.ActivityMainBinding
import com.davidwxcui.waterwise.ui.onboarding.OnboardingActivity

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check if onboarding is completed
        val onboardingPrefs = OnboardingPreferences(this)
        if (!onboardingPrefs.isOnboardingCompleted()) {
            // Redirect to onboarding
            val intent = Intent(this, OnboardingActivity::class.java)
            startActivity(intent)
            finish()
            return
        }

        supportActionBar?.hide()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get NavController from the NavHostFragment
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
        val navController = navHostFragment.navController

        // Connect BottomNavigationView to NavController
        binding.navView.setupWithNavController(navController)

        // FAB action: only if current fragment is HomeFragment
        binding.fabAdd.setOnClickListener {
            val currentFragment = navHostFragment.childFragmentManager.fragments.firstOrNull()
            if (currentFragment is com.davidwxcui.waterwise.ui.home.HomeFragment) {
                currentFragment.showFabQuickAdd()
            }
        }
    }
}
