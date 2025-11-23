package com.davidwxcui.waterwise.ui.profile

import android.content.Context
import android.os.Bundle
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.davidwxcui.waterwise.databinding.FragmentRegisterBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.UUID

class RegisterFragment : Fragment() {

    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!

    /**
     * Backend placeholder. Replace this with real network call later.
     */
    private interface ProfileApi {
        suspend fun register(name: String, email: String, password: String): Result<String> // return token
    }

    // Demo implementation, local only.
    private val api: ProfileApi = object : ProfileApi {
        override suspend fun register(name: String, email: String, password: String): Result<String> =
            withContext(Dispatchers.IO) {
                if (name.isNotBlank() && email.isNotBlank() && password.isNotBlank()) {
                    Result.success("local_register_token")
                } else {
                    Result.failure(Exception("Please fill all fields"))
                }
            }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        fun validateName(): Boolean {
            val name = binding.etName.text?.toString()?.trim().orEmpty()
            val ok = name.length in 2..30
            binding.etName.error = if (ok) null else "Name 2–30 chars"
            return ok
        }

        fun validateEmail(): Boolean {
            val email = binding.etEmail.text?.toString()?.trim()?.lowercase().orEmpty()
            val ok = Patterns.EMAIL_ADDRESS.matcher(email).matches()
            binding.etEmail.error = if (ok) null else "Invalid email"
            return ok
        }

        fun validatePassword(): Boolean {
            val pw = binding.etPassword.text?.toString().orEmpty()
            val okLen = pw.length >= 8
            val okNoSpace = !pw.contains(" ")
            val okLetter = pw.any { it.isLetter() }
            val okDigit = pw.any { it.isDigit() }
            val ok = okLen && okNoSpace && okLetter && okDigit
            binding.etPassword.error = if (ok) null else "Min 8 chars, letters+digits, no spaces"
            return ok
        }

        fun validatePassword2(): Boolean {
            val pw1 = binding.etPassword.text?.toString().orEmpty()
            val pw2 = binding.etPassword2.text?.toString().orEmpty()
            val ok = pw1 == pw2 && pw2.isNotBlank()
            binding.etPassword2.error = if (ok) null else "Passwords do not match"
            return ok
        }

        fun updateRegisterEnabled(): Boolean {
            val ok = validateName() && validateEmail() && validatePassword() && validatePassword2()
            binding.btnRegister.isEnabled = ok
            return ok
        }

        binding.etName.doAfterTextChanged { updateRegisterEnabled() }
        binding.etEmail.doAfterTextChanged { updateRegisterEnabled() }
        binding.etPassword.doAfterTextChanged { updateRegisterEnabled() }
        binding.etPassword2.doAfterTextChanged { updateRegisterEnabled() }
        updateRegisterEnabled()

        binding.btnRegister.setOnClickListener {
            val name = binding.etName.text?.toString()?.trim().orEmpty()
            val email = binding.etEmail.text?.toString()?.trim()?.lowercase().orEmpty()
            val pw1 = binding.etPassword.text?.toString().orEmpty()

            if (!updateRegisterEnabled()) {
                Snackbar.make(binding.root, "Please fix errors", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val sp = requireContext().getSharedPreferences(AUTH_FILE, Context.MODE_PRIVATE)
            val existingEmail = sp.getString(KEY_REGISTERED_EMAIL, null)

            if (!existingEmail.isNullOrBlank() && existingEmail == email) {
                Snackbar.make(binding.root, "Account already exists. Please login.", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Short UID (10 chars)
            val uid = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .take(10)

            val pwdHash = sha256(pw1)

            binding.btnRegister.isEnabled = false

            lifecycleScope.launch {
                val res = api.register(name, email, pw1)
                if (res.isFailure) {
                    Snackbar.make(binding.root, res.exceptionOrNull()?.message ?: "Register failed", Snackbar.LENGTH_SHORT).show()
                    binding.btnRegister.isEnabled = true
                    return@launch
                }

                val token = res.getOrNull()!!

                // Save local auth
                sp.edit()
                    .putString(KEY_UID, uid)
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
                findNavController().popBackStack() // back to Login
                findNavController().popBackStack() // back to Profile
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
