package com.davidwxcui.waterwise

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import com.davidwxcui.waterwise.databinding.ActivityMainBinding
import com.davidwxcui.waterwise.ui.home.HomeFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var currentDest: Int = R.id.nav_home

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // NO ActionBar
        supportActionBar?.hide()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState == null) {
            openHome()
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_home -> {
                    if (currentDest != R.id.navigation_home) openHome()
                    currentDest = R.id.navigation_home
                    true
                }
                R.id.navigation_dashboard,
                R.id.navigation_notifications,
                R.id.navigation_profile -> {
                    // Placeholder
                    currentDest = item.itemId
                    true
                }
                else -> false
            }
        }

        binding.fab.setOnClickListener {
            val f = supportFragmentManager.findFragmentById(binding.container.id)
            if (f is HomeFragment) {
                f.showFabQuickAdd()
            }
        }

        binding.bottomNav.selectedItemId = R.id.navigation_home
    }

    private fun openHome() {
        supportFragmentManager.commit {
            replace(binding.container.id, HomeFragment())
        }
    }
}
