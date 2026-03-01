package com.fizzzli.listapp

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class ListAppApplication : Application() {
    
    @Inject
    lateinit var databaseSeeder: DatabaseSeeder
    
    override fun onCreate() {
        super.onCreate()
        
        // Seed database on first launch
        CoroutineScope(Dispatchers.IO).launch {
            databaseSeeder.seed()
        }
    }
}
