package com.example.buswatch.driver

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.example.buswatch.driver.databinding.FragmentDriverSettingsBinding
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.example.buswatch.common.R as CommonR

class SettingsFragment : Fragment() {

    private var _binding: FragmentDriverSettingsBinding? = null
    private val binding get() = _binding!!
    
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDriverSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupBottomNav()
        setupSupportSection()
        setupDeleteAccount()
        
        binding.btnLogout.setOnClickListener {
            val prefs = requireContext().getSharedPreferences("BusWatchPrefs", Context.MODE_PRIVATE)
            val isDemo = prefs.getBoolean("is_demo", false)
            
            auth.signOut()
            
            try {
                val targetClass = if (isDemo) {
                    Class.forName("com.example.buswatch.DemoLogin")
                } else {
                    Class.forName("com.example.buswatch.Login")
                }
                val intent = Intent(requireContext(), targetClass)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                requireActivity().finish()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun setupDeleteAccount() {
        binding.btnDeleteAccount.setOnClickListener {
            val prefs = requireContext().getSharedPreferences("BusWatchPrefs", Context.MODE_PRIVATE)
            val isDemo = prefs.getBoolean("is_demo", false)
            
            if (isDemo) {
                Toast.makeText(requireContext(), "Demo accounts cannot be deleted.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            showDeletionAgreementDialog()
        }
    }

    private fun showDeletionAgreementDialog() {
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Account Deletion Agreement")
            .setMessage("By proceeding, you agree that your driver account and all associated records will be permanently removed. This action is irreversible.\n\nPlease wait 10 seconds to continue.")
            .setCancelable(false)
            .setPositiveButton("AGREE", null)
            .setNegativeButton(CommonR.string.cancel_caps, null)
            .create()

        dialog.setOnShowListener {
            val agreeBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            agreeBtn.isEnabled = false
            agreeBtn.setTextColor(Color.GRAY)

            val timer = object : CountDownTimer(10000, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    val secondsLeft = (millisUntilFinished / 1000) + 1
                    dialog.setMessage("By proceeding, you agree that your driver account and all associated records will be permanently removed. This action is irreversible.\n\nPlease wait $secondsLeft seconds to continue.")
                }

                override fun onFinish() {
                    dialog.setMessage("Agreement accepted. You can now proceed.")
                    agreeBtn.isEnabled = true
                    agreeBtn.setTextColor(Color.parseColor("#008577"))
                }
            }.start()

            agreeBtn.setOnClickListener {
                dialog.dismiss()
                showFinalDeleteConfirmationDialog()
            }

            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                timer.cancel()
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun showFinalDeleteConfirmationDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_delete_account, null)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvDeleteTitle)
        val tvMsg = dialogView.findViewById<TextView>(R.id.tvDeleteTimerMsg)
        val etPassword = dialogView.findViewById<EditText>(R.id.etDeletePassword)
        val etConfirm = dialogView.findViewById<EditText>(R.id.etDeleteConfirm)
        
        tvTitle.text = "Final Confirmation"
        tvMsg.text = "Please type 'DELETE' and enter your password below to confirm your decision."

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(false)
            .setPositiveButton("CONFIRM", null)
            .setNegativeButton(CommonR.string.cancel_caps, null)
            .create()

        dialog.setOnShowListener {
            val confirmBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            confirmBtn.isEnabled = false
            confirmBtn.setTextColor(Color.GRAY)

            val checkInputs = {
                val typedConfirm = etConfirm.text.toString().trim()
                val password = etPassword.text.toString().trim()
                if (typedConfirm == "DELETE" && password.isNotEmpty()) {
                    confirmBtn.isEnabled = true
                    confirmBtn.setTextColor(Color.RED)
                } else {
                    confirmBtn.isEnabled = false
                    confirmBtn.setTextColor(Color.GRAY)
                }
            }

            etPassword.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { checkInputs() }
                override fun afterTextChanged(s: Editable?) {}
            })

            etConfirm.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { checkInputs() }
                override fun afterTextChanged(s: Editable?) {}
            })

            confirmBtn.setOnClickListener {
                val password = etPassword.text.toString().trim()
                deleteAccountPermanently(password)
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun deleteAccountPermanently(password: String) {
        val user = auth.currentUser ?: return
        val uid = user.uid
        val email = user.email ?: ""

        if (email.isEmpty()) {
            Toast.makeText(requireContext(), "User email not found. Please log in again.", Toast.LENGTH_SHORT).show()
            return
        }

        val credential = EmailAuthProvider.getCredential(email, password)
        user.reauthenticate(credential).addOnSuccessListener {
            // 1. Delete Firestore Data
            db.collection("drivers").document(uid).delete().addOnCompleteListener {
                // 2. Delete Auth User
                user.delete().addOnSuccessListener {
                    if (isAdded) {
                        Toast.makeText(requireContext(), "Your account has been deleted.", Toast.LENGTH_LONG).show()
                        try {
                            val loginClass = Class.forName("com.example.buswatch.Login")
                            val intent = Intent(requireContext(), loginClass)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            startActivity(intent)
                            requireActivity().finish()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }.addOnFailureListener { e ->
                    if (isAdded) Toast.makeText(requireContext(), "Auth Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.addOnFailureListener {
            if (isAdded) Toast.makeText(requireContext(), "Verification Failed: Incorrect Password", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupSupportSection() {
        binding.layoutFAQ.setOnClickListener {
            try {
                val faqFragmentClass = Class.forName("com.example.buswatch.FAQFragment")
                val faqFragment = faqFragmentClass.getDeclaredConstructor().newInstance() as Fragment
                parentFragmentManager.beginTransaction()
                    .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out, android.R.anim.fade_in, android.R.anim.fade_out)
                    .replace(R.id.fragment_container, faqFragment)
                    .addToBackStack(null)
                    .commit()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        binding.layoutTerms.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out, android.R.anim.fade_in, android.R.anim.fade_out)
                .replace(R.id.fragment_container, TermsConditionsFragment())
                .addToBackStack(null)
                .commit()
        }

        binding.layoutPrivacy.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out, android.R.anim.fade_in, android.R.anim.fade_out)
                .replace(R.id.fragment_container, PrivacyPolicyFragment())
                .addToBackStack(null)
                .commit()
        }
    }

    private fun setupBottomNav() {
        binding.containerNavHome.setOnClickListener { (activity as? DriverHome)?.loadHome() }
        binding.containerNavAccount.setOnClickListener { (activity as? DriverHome)?.loadAccount() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
