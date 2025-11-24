package com.davidwxcui.waterwise

import android.app.Application
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings

class WaterwiseApp : Application() {
    override fun onCreate() {
        super.onCreate()

        val db = FirebaseFirestore.getInstance()

        val settings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true) // 开启离线缓存
            // .setCacheSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED) // 如需无限缓存再打开
            .build()

        db.firestoreSettings = settings
    }
}
