package com.davidwxcui.waterwise

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.davidwxcui.waterwise.data.OnboardingPreferences
import com.davidwxcui.waterwise.databinding.ActivityMainBinding
import com.davidwxcui.waterwise.ui.onboarding.OnboardingActivity
import com.davidwxcui.waterwise.ui.profile.FirebaseAuthRepository

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check if user is logged in AND onboarding is not completed
        val onboardingPrefs = OnboardingPreferences(this)
        val isLoggedIn = FirebaseAuthRepository.isLoggedIn()

        if (isLoggedIn && !onboardingPrefs.isOnboardingCompleted()) {
            // User is logged in but hasn't completed onboarding
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

        // When in  Friends / FriendRequests Page High light Home tab
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
}
