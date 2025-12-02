package com.davidwxcui.waterwise.ui.onboarding

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import com.davidwxcui.waterwise.R
import com.davidwxcui.waterwise.databinding.ActivityOnboardingBinding
import com.davidwxcui.waterwise.ui.profile.FirebaseAuthRepository

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check if user is logged in before proceeding with onboarding
        if (!FirebaseAuthRepository.isLoggedIn()) {
            finish()
            return
        }

        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Hide action bar for clean onboarding experience
        supportActionBar?.hide()
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_onboarding)
        return navController.navigateUp() || super.onSupportNavigateUp()
    }
}

