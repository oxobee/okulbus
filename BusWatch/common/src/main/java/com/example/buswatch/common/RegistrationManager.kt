package com.example.buswatch.common

/**
 * A secure, in-memory singleton to hold registration data across activities.
 * This prevents passing sensitive information like passwords through Intent extras.
 */
object RegistrationManager {
    var password: String? = null
    
    // Clear data after registration is complete or failed
    fun clear() {
        password = null
    }
}
