package com.davidwxcui.waterwise.ui.profile

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.davidwxcui.waterwise.data.OnboardingPreferences
import com.davidwxcui.waterwise.databinding.FragmentRegisterBinding
import com.davidwxcui.waterwise.ui.onboarding.OnboardingActivity
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.util.UUID

class RegisterFragment : Fragment() {

    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!

    // Check User is leave
    private var nameTouched = false
    private var emailTouched = false
    private var pwTouched = false
    private var pw2Touched = false

    /**
     * Backend placeholder
     */
    private val api: AuthApi = FirebaseAuthRepository

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        fun validateName(showError: Boolean): Boolean {
            val name = binding.etName.text?.toString()?.trim().orEmpty()
            val ok = name.length in 2..30
            if (ok) {
                binding.etName.error = null
            } else if (showError) {
                binding.etName.error = "Name 2–30 chars"
            }
            return ok
        }

        fun validateEmail(showError: Boolean): Boolean {
            val email = binding.etEmail.text?.toString()?.trim()?.lowercase().orEmpty()
            val ok = Patterns.EMAIL_ADDRESS.matcher(email).matches()
            if (ok) {
                binding.etEmail.error = null
            } else if (showError) {
                binding.etEmail.error = "Invalid email"
            }
            return ok
        }

        fun validatePassword(showError: Boolean): Boolean {
            val pw = binding.etPassword.text?.toString().orEmpty()
            val okLen = pw.length >= 8
            val okNoSpace = !pw.contains(" ")
            val okLetter = pw.any { it.isLetter() }
            val okDigit = pw.any { it.isDigit() }
            val ok = okLen && okNoSpace && okLetter && okDigit
            if (ok) {
                binding.etPassword.error = null
            } else if (showError) {
                binding.etPassword.error = "Min 8 chars, letters+digits, no spaces"
            }
            return ok
        }

        fun validatePassword2(showError: Boolean): Boolean {
            val pw1 = binding.etPassword.text?.toString().orEmpty()
            val pw2 = binding.etPassword2.text?.toString().orEmpty()
            val ok = pw1 == pw2 && pw2.isNotBlank()
            if (ok) {
                binding.etPassword2.error = null
            } else if (showError) {
                binding.etPassword2.error = "Passwords do not match"
            }
            return ok
        }

        fun updateRegisterEnabled(): Boolean {
            val ok = validateName(false) &&
                    validateEmail(false) &&
                    validatePassword(false) &&
                    validatePassword2(false)
            binding.btnRegister.isEnabled = ok
            return ok
        }

        binding.etName.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                nameTouched = true
                validateName(true)
            }
        }
        binding.etEmail.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                emailTouched = true
                validateEmail(true)
            }
        }
        binding.etPassword.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                pwTouched = true
                validatePassword(true)
            }
        }
        binding.etPassword2.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                pw2Touched = true
                validatePassword2(true)
            }
        }

        binding.etName.doAfterTextChanged {
            validateName(nameTouched)
            updateRegisterEnabled()
        }
        binding.etEmail.doAfterTextChanged {
            validateEmail(emailTouched)
            updateRegisterEnabled()
        }
        binding.etPassword.doAfterTextChanged {
            validatePassword(pwTouched)
            validatePassword2(pw2Touched)
            updateRegisterEnabled()
        }
        binding.etPassword2.doAfterTextChanged {
            validatePassword2(pw2Touched)
            updateRegisterEnabled()
        }

        updateRegisterEnabled()

        binding.btnRegister.setOnClickListener {
            nameTouched = true
            emailTouched = true
            pwTouched = true
            pw2Touched = true

            val ok = validateName(true) &&
                    validateEmail(true) &&
                    validatePassword(true) &&
                    validatePassword2(true)

            if (!ok) {
                Snackbar.make(binding.root, "Please fix errors", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val name = binding.etName.text?.toString()?.trim().orEmpty()
            val email = binding.etEmail.text?.toString()?.trim()?.lowercase().orEmpty()
            val pw1 = binding.etPassword.text?.toString().orEmpty()

            val sp = requireContext().getSharedPreferences(AUTH_FILE, Context.MODE_PRIVATE)
            val existingEmail = sp.getString(KEY_REGISTERED_EMAIL, null)

            if (!existingEmail.isNullOrBlank() && existingEmail == email) {
                Snackbar.make(binding.root, "Account already exists. Please login.", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Short UID (10 chars)
            val legacyLocalUid = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .take(10)

            val pwdHash = sha256(pw1)

            binding.btnRegister.isEnabled = false

            lifecycleScope.launch {
                val res = api.register(requireContext(), name, email, pw1)
                if (res.isFailure) {
                    Snackbar.make(binding.root, res.exceptionOrNull()?.message ?: "Register failed", Snackbar.LENGTH_SHORT).show()
                    binding.btnRegister.isEnabled = true
                    return@launch
                }

                val user = res.getOrNull()!!
                val uid = user.uid
                val token = user.token

                // Save local auth
                sp.edit()
                    .putString(KEY_UID, uid) // use Firebase uid
                    .putString(KEY_REGISTERED_EMAIL, email)
                    .putString(KEY_REGISTERED_PWD_HASH, pwdHash)
                    .putBoolean(KEY_LOGGED_IN, true)
                    .putString(KEY_TOKEN, token)
                    .apply()

                /**
                 * IMPORTANT:
                 * Create a brand-new empty profile.
                 * Do NOT copy old guest profile.
                 */
                val blankProfile = Profile(
                    name = name,
                    email = email,
                    age = 0,
                    sex = Sex.UNSPECIFIED,
                    heightCm = 0,
                    weightKg = 0,
                    activity = ActivityLevel.SEDENTARY,
                    activityFreqLabel = "",
                    avatarUri = null
                )
                ProfilePrefs.save(requireContext(), blankProfile)

                Snackbar.make(binding.root, "Register successful", Snackbar.LENGTH_SHORT).show()

                // Check if onboarding is completed
                val onboardingPrefs = OnboardingPreferences(requireContext())
                if (!onboardingPrefs.isOnboardingCompleted()) {
                    // Launch onboarding activity immediately
                    val intent = Intent(requireContext(), OnboardingActivity::class.java)
                    startActivity(intent)
                    requireActivity().finish()
                } else {
                    // Just go back to profile
                    findNavController().popBackStack() // back to Login
                    findNavController().popBackStack() // back to Profile
                }
            }.invokeOnCompletion {
                binding.btnRegister.isEnabled = true
            }
        }

        binding.btnCancel.setOnClickListener {
            findNavController().popBackStack()
        }
    }

    private fun sha256(s: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val AUTH_FILE = "profile"
        private const val KEY_UID = "uid"
        private const val KEY_TOKEN = "auth_token"
        private const val KEY_LOGGED_IN = "loggedIn"

        private const val KEY_REGISTERED_EMAIL = "registered_email"
        private const val KEY_REGISTERED_PWD_HASH = "registered_pwd_hash"
    }
}
