package com.davidwxcui.waterwise

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import com.davidwxcui.waterwise.databinding.ActivityMainBinding
import com.davidwxcui.waterwise.ui.home.HomeFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var currentDest: Int = R.id.navigation_home

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportActionBar?.hide()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initial fragment
        if (savedInstanceState == null) {
            openHome()
        }

        // Bottom navigation
        binding.navView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_home -> {
                    if (currentDest != R.id.navigation_home) openHome()
                    currentDest = R.id.navigation_home
                    true
                }
                R.id.navigation_dashboard,
                R.id.navigation_notifications,
                R.id.navigation_profile -> {
                    // Placeholder for other tabs
                    currentDest = item.itemId
                    true
                }
                else -> false
            }
        }

        // FAB click
        binding.fabAdd.setOnClickListener {
            val f = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main)
            if (f is HomeFragment) {
                f.showFabQuickAdd()
            }
        }

        // Set default selected tab
        binding.navView.selectedItemId = R.id.navigation_home
    }

    private fun openHome() {
        supportFragmentManager.commit {
            replace(R.id.nav_host_fragment_activity_main, HomeFragment())
        }
    }
}
