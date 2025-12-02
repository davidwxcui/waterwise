package com.davidwxcui.waterwise.minigame

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.davidwxcui.waterwise.R

class GameRankingActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportActionBar?.hide()
        setContentView(R.layout.activity_game_ranking)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.gameRankingContainer, GameRankingFragment())
                .commit()
        }
    }
}

