package com.example.buswatch

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.example.buswatch.common.R as CommonR
import com.onesignal.OneSignal
import com.google.android.material.materialswitch.MaterialSwitch

class Login : AppCompatActivity() {
    private var isPasswordVisible = false
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var progressBar: ProgressBar

    // Using reflection-safe property getters to prevent class-loading crashes if BuildConfig is missing fields
    private val adminEmail: String get() = getStringBuildConfig("ADMIN_EMAIL")
    private val adminPassword: String get() = getStringBuildConfig("ADMIN_PASSWORD")
    private val driverEmail: String get() = getStringBuildConfig("DRIVER_EMAIL")
    private val driverPassword: String get() = getStringBuildConfig("DRIVER_PASSWORD")

    private fun getStringBuildConfig(fieldName: String): String {
        return try {
            val clazz = Class.forName("${packageName}.BuildConfig")
            val field = clazz.getField(fieldName)
            field.get(null) as String
        } catch (t: Throwable) {
            Log.w("Login", "BuildConfig field $fieldName not found: ${t.message}")
            ""
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.login)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        
        val emailEditText = findViewById<EditText>(R.id.etLoginEmail)
        val passwordEditText = findViewById<EditText>(R.id.etLoginPassword)
        val viewPasswordButton = findViewById<ImageButton>(R.id.btnLoginViewPassword)
        val loginButton = findViewById<Button>(R.id.btnLoginLogin)
        val signupButton = findViewById<Button>(R.id.btnLoginSignup)
        val forgotPasswordButton = findViewById<Button>(R.id.btnLoginForgotPassword)
        val swDemoMode = findViewById<MaterialSwitch>(R.id.swDemoMode)
        progressBar = findViewById(R.id.progressBar)

        // Safety check for auto-login
        auth.currentUser?.let { user ->
            try {
                OneSignal.login(user.uid)
            } catch (t: Throwable) {
                Log.e("Login", "OneSignal auto-login failed: ${t.message}")
            }
            checkUserRole(user.uid)
        }

        swDemoMode.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                try {
                    startActivity(Intent(this, DemoLogin::class.java))
                } catch (t: Throwable) {
                    Toast.makeText(this, "Demo Mode unavailable", Toast.LENGTH_SHORT).show()
                }
                swDemoMode.isChecked = false
            }
        }

        viewPasswordButton.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            passwordEditText.transformationMethod = if (isPasswordVisible) 
                HideReturnsTransformationMethod.getInstance() else PasswordTransformationMethod.getInstance()
            viewPasswordButton.setImageResource(if (isPasswordVisible) CommonR.drawable.ic_eye else CommonR.drawable.ic_eye_off)
            passwordEditText.setSelection(passwordEditText.text.length)
        }

        loginButton.setOnClickListener {
            Log.d("Login", "Login button clicked")
            var emailInput = emailEditText.text.toString().trim()
            var passwordInput = passwordEditText.text.toString().trim()

            // Safe check for shortcuts
            if (emailInput.lowercase() == "admin") {
                emailInput = adminEmail
                if (passwordInput.lowercase() == "admin") passwordInput = adminPassword
            } else if (emailInput.lowercase() == "driver") {
                emailInput = driverEmail
                if (passwordInput.lowercase() == "driver") passwordInput = driverPassword
            }

            if (emailInput.isEmpty() || passwordInput.isEmpty()) {
                Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            performFirebaseLogin(emailInput, passwordInput)
        }

        signupButton.setOnClickListener { 
            try {
                startActivity(Intent(this, Signup1::class.java))
            } catch (t: Throwable) {
                Log.e("Login", "Signup failed to open: ${t.message}")
            }
        }
        
        forgotPasswordButton.setOnClickListener { 
            try {
                startActivity(Intent(this, ForgotPassword::class.java))
            } catch (t: Throwable) {
                Log.e("Login", "ForgotPassword failed to open: ${t.message}")
            }
        }
    }

    private fun performFirebaseLogin(email: String, pass: String) {
        val btnLogin = findViewById<Button>(R.id.btnLoginLogin)
        btnLogin.isEnabled = false
        progressBar.visibility = View.VISIBLE

        auth.signInWithEmailAndPassword(email, pass)
            .addOnCompleteListener { task ->
                if (isFinishing) return@addOnCompleteListener
                
                if (task.isSuccessful) {
                    val uid = auth.currentUser?.uid
                    if (uid != null) {
                        getSharedPreferences("BusWatchPrefs", MODE_PRIVATE).edit { putBoolean("is_demo", false) }
                        
                        try {
                            OneSignal.login(uid)
                        } catch (t: Throwable) {
                            Log.e("Login", "OneSignal login failed: ${t.message}")
                        }
                        
                        checkUserRole(uid)
                    } else {
                        resetLoginState()
                    }
                } else {
                    // Check for Admin Creation Fallback
                    if (email == adminEmail && pass == adminPassword && email.isNotEmpty()) {
                        fallbackAdminSetup()
                    } else {
                        resetLoginState()
                        Toast.makeText(this, "Login Failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
    }

    private fun fallbackAdminSetup() {
        auth.createUserWithEmailAndPassword(adminEmail, adminPassword)
            .addOnCompleteListener { task ->
                if (isFinishing) return@addOnCompleteListener
                if (task.isSuccessful) {
                    val uid = auth.currentUser?.uid ?: return@addOnCompleteListener
                    createAdminFirestoreDoc(uid)
                    navigateBasedOnRole("admin")
                } else {
                    resetLoginState()
                    Toast.makeText(this, "Login failed. Incorrect credentials.", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun createAdminFirestoreDoc(uid: String) {
        val adminData = hashMapOf(
            "role" to "admin",
            "firstName" to "System",
            "lastName" to "Admin",
            "email" to adminEmail,
            "status" to "approved"
        )
        db.collection("admin").document(uid).set(adminData)
    }

    private fun checkUserRole(uid: String) {
        if (auth.currentUser?.email == adminEmail && adminEmail.isNotEmpty()) {
            navigateBasedOnRole("admin")
            return
        }

        db.collection("admin").document(uid).get().addOnSuccessListener { doc ->
            if (isFinishing) return@addOnSuccessListener
            if (doc.exists()) {
                if (doc.getString("status")?.lowercase() == "archived") handleArchivedUser()
                else navigateBasedOnRole("admin")
            } else {
                searchOtherRoles(uid)
            }
        }.addOnFailureListener { 
            if (!isFinishing) resetLoginState() 
        }
    }

    private fun searchOtherRoles(uid: String) {
        db.collection("parents").document(uid).get().addOnSuccessListener { doc ->
            if (isFinishing) return@addOnSuccessListener
            if (doc.exists()) {
                if (doc.getString("status")?.lowercase() == "archived") handleArchivedUser()
                else navigateBasedOnRole(doc.getString("role") ?: "parent")
            } else {
                db.collection("drivers").document(uid).get().addOnSuccessListener { dDoc ->
                    if (dDoc.exists()) {
                        if (dDoc.getString("status")?.lowercase() == "archived") handleArchivedUser()
                        else navigateBasedOnRole("driver")
                    } else {
                        db.collection("conductors").document(uid).get().addOnSuccessListener { cDoc ->
                            if (cDoc.exists()) {
                                if (cDoc.getString("status")?.lowercase() == "archived") handleArchivedUser()
                                else navigateBasedOnRole("conductor")
                            } else navigateBasedOnRole("parent")
                        }
                    }
                }
            }
        }
    }

    private fun handleArchivedUser() {
        auth.signOut()
        resetLoginState()
        Toast.makeText(this, "Account deactivated. Contact Admin.", Toast.LENGTH_LONG).show()
    }

    private fun resetLoginState() {
        findViewById<Button>(R.id.btnLoginLogin).isEnabled = true
        progressBar.visibility = View.GONE
    }

    private fun navigateBasedOnRole(role: String?) {
        try {
            val intent = when (role?.lowercase()) {
                "admin" -> Intent(this, com.example.buswatch.admin.AdminHome::class.java)
                "driver", "conductor" -> Intent(this, com.example.buswatch.driver.DriverHome::class.java)
                else -> Intent(this, ParentMainActivity::class.java)
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(intent)
            finish()
        } catch (t: Throwable) {
            Log.e("Login", "Navigation failed: ${t.message}", t)
            resetLoginState()
            Toast.makeText(this, "Navigation error. Please restart the app.", Toast.LENGTH_SHORT).show()
        }
    }
}
