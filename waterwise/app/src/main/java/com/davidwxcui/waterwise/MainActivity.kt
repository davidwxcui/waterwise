package com.davidwxcui.waterwise

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.davidwxcui.waterwise.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
