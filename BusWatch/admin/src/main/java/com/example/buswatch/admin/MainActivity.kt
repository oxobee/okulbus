package com.example.buswatch.admin

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Removed redundant Osmdroid configuration. 
        // Single point of truth is now in BusWatchApp.kt to prevent Tile Usage Policy blocking.

        // Redirect to AdminHome
        val intent = Intent(this, AdminHome::class.java)
        startActivity(intent)
        finish()
    }
}
