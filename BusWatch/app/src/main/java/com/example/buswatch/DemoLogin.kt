package com.example.buswatch

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.firestore.FirebaseFirestore
import com.onesignal.OneSignal
import com.google.android.material.materialswitch.MaterialSwitch

class DemoLogin : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var progressBar: ProgressBar

    private fun getDemoString(fieldName: String): String {
        return try {
            val clazz = Class.forName("${packageName}.BuildConfig")
            val field = clazz.getField(fieldName)
            field.get(null) as String
        } catch (t: Throwable) {
            ""
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.demo_login)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val roleSpinner = findViewById<Spinner>(R.id.spDemoRole)
        val loginButton = findViewById<Button>(R.id.btnDemoLogin)
        val backButton = findViewById<ImageButton>(R.id.btnBack)
        val swLoginMode = findViewById<MaterialSwitch>(R.id.swLoginMode)
        progressBar = findViewById(R.id.progressBar)

        backButton.setOnClickListener { finish() }
        
        swLoginMode.setOnCheckedChangeListener { view, isChecked ->
            if (!isChecked) {
                view.post {
                    if (!isFinishing) {
                        val intent = Intent(this, Login::class.java)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        startActivity(intent)
                        finish()
                    }
                }
            }
        }

        loginButton.setOnClickListener {
            val selectedRole = roleSpinner.selectedItem.toString()
            val email = when (selectedRole) {
                "Parent" -> getDemoString("DEMO_PARENT_EMAIL")
                "Driver" -> getDemoString("DEMO_DRIVER_EMAIL")
                "Conductor" -> getDemoString("DEMO_CONDUCTOR_EMAIL")
                else -> ""
            }
            val password = when (selectedRole) {
                "Parent" -> getDemoString("DEMO_PARENT_PASS")
                "Driver" -> getDemoString("DEMO_DRIVER_PASS")
                "Conductor" -> getDemoString("DEMO_CONDUCTOR_PASS")
                else -> ""
            }

            if (email.isNotEmpty() && password.isNotEmpty()) {
                performDemoLogin(email, password, selectedRole)
            } else {
                Toast.makeText(this, "Demo credentials missing for $selectedRole", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun performDemoLogin(email: String, pass: String, role: String) {
        findViewById<Button>(R.id.btnDemoLogin).isEnabled = false
        progressBar.visibility = View.VISIBLE

        auth.signInWithEmailAndPassword(email, pass)
            .addOnCompleteListener { task ->
                if (isFinishing) return@addOnCompleteListener
                if (task.isSuccessful) {
                    onAuthSuccess(auth.currentUser?.uid, role)
                } else {
                    if (task.exception is FirebaseAuthInvalidUserException) {
                        createDemoAuthUser(email, pass, role)
                    } else {
                        resetUI()
                        Toast.makeText(this, "Login Failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
    }

    private fun createDemoAuthUser(email: String, pass: String, role: String) {
        auth.createUserWithEmailAndPassword(email, pass)
            .addOnCompleteListener { task ->
                if (isFinishing) return@addOnCompleteListener
                if (task.isSuccessful) onAuthSuccess(auth.currentUser?.uid, role)
                else {
                    resetUI()
                    Toast.makeText(this, "Setup Failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun onAuthSuccess(uid: String?, role: String) {
        if (uid == null) {
            resetUI()
            return
        }
        getSharedPreferences("BusWatchPrefs", Context.MODE_PRIVATE).edit().putBoolean("is_demo", true).apply()
        try { OneSignal.login(uid) } catch (e: Exception) {}

        val collection = when (role) {
            "Parent" -> "parents"
            "Driver" -> "drivers"
            "Conductor" -> "conductors"
            else -> "parents"
        }

        db.collection(collection).document(uid).get().addOnSuccessListener { doc ->
            if (isFinishing) return@addOnSuccessListener
            if (!doc.exists()) {
                val demoData = hashMapOf(
                    "firstName" to "Demo",
                    "lastName" to role,
                    "email" to auth.currentUser?.email,
                    "role" to role,
                    "status" to "approved"
                )
                db.collection(collection).document(uid).set(demoData).addOnSuccessListener { navigateBasedOnRole(role) }
            } else {
                navigateBasedOnRole(role)
            }
        }.addOnFailureListener { if (!isFinishing) navigateBasedOnRole(role) }
    }

    private fun resetUI() {
        findViewById<Button>(R.id.btnDemoLogin).isEnabled = true
        progressBar.visibility = View.GONE
    }

    private fun navigateBasedOnRole(role: String) {
        try {
            val intent = when (role.lowercase()) {
                "admin" -> Intent(this, com.example.buswatch.admin.AdminHome::class.java)
                "driver", "conductor" -> Intent(this, com.example.buswatch.driver.DriverHome::class.java)
                else -> Intent(this, ParentMainActivity::class.java)
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            resetUI()
        }
    }
}
