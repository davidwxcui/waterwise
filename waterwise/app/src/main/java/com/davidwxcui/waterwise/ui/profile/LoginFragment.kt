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
import com.davidwxcui.waterwise.R
import com.davidwxcui.waterwise.databinding.FragmentLoginBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.ceil

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    // backend
    private val api: AuthApi = FirebaseAuthRepository

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        val local = ProfilePrefs.load(requireContext())
        binding.tvAvatarInitial.text = (local.name.firstOrNull() ?: 'U')
            .uppercaseChar().toString()

        // Real-time validation
        fun validateInputs(): Boolean {
            val email = binding.etEmail.text?.toString()?.trim()?.lowercase().orEmpty()
            val pw = binding.etPassword.text?.toString()?.trim().orEmpty()

            val emailOk = Patterns.EMAIL_ADDRESS.matcher(email).matches()
            val pwOk = pw.length >= 8 && !pw.contains(" ")
            binding.etEmail.error = if (emailOk) null else "Invalid email"
            binding.etPassword.error = if (pwOk) null else "Min 8 chars, no spaces"

            val ok = emailOk && pwOk
            binding.btnLogin.isEnabled = ok
            return ok
        }

        binding.etEmail.doAfterTextChanged { validateInputs() }
        binding.etPassword.doAfterTextChanged { validateInputs() }
        validateInputs()

        // Login click
        binding.btnLogin.setOnClickListener {
            if (!validateInputs()) {
                Snackbar.make(binding.root, "Please fix errors", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val email = binding.etEmail.text!!.trim().toString().lowercase()
            val pw = binding.etPassword.text!!.trim().toString()

            val sp = requireContext().getSharedPreferences(AUTH_FILE, Context.MODE_PRIVATE)

            val lockUntil = sp.getLong(KEY_LOCK_UNTIL, 0L)
            val now = System.currentTimeMillis()
            if (now < lockUntil) {
                val secLeft = ceil((lockUntil - now) / 1000.0).toInt()
                Snackbar.make(binding.root, "Too many attempts. Try again in ${secLeft}s", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            binding.btnLogin.isEnabled = false

            lifecycleScope.launch {
                val result = api.login(requireContext(), email, pw)
                if (result.isSuccess) {
                    val user = result.getOrNull()!!

                    sp.edit()
                        .putInt(KEY_FAIL_COUNT, 0)
                        .putLong(KEY_LOCK_UNTIL, 0L)
                        .putString(KEY_UID, user.uid)
                        .putString(KEY_TOKEN, user.token)
                        .putBoolean(KEY_LOGGED_IN, true)
                        .apply()

                    Snackbar.make(binding.root, "Login successful", Snackbar.LENGTH_SHORT).show()
                    delay(200)
                    findNavController().popBackStack()
                } else {
                    val fail = sp.getInt(KEY_FAIL_COUNT, 0) + 1
                    val editor = sp.edit().putInt(KEY_FAIL_COUNT, fail)
                    if (fail >= 5) {
                        editor.putLong(KEY_LOCK_UNTIL, System.currentTimeMillis() + 30_000L)
                            .putInt(KEY_FAIL_COUNT, 0)
                            .apply()
                        Snackbar.make(binding.root, "Locked for 30s after 5 failures", Snackbar.LENGTH_SHORT).show()
                    } else {
                        editor.apply()
                        Snackbar.make(
                            binding.root,
                            result.exceptionOrNull()?.message ?: "Login failed ($fail/5)",
                            Snackbar.LENGTH_SHORT
                        ).show()
                    }
                }
                binding.btnLogin.isEnabled = true
            }
        }

        binding.btnCancel.setOnClickListener { findNavController().popBackStack() }
        binding.tvGoRegister.setOnClickListener {
            findNavController().navigate(R.id.action_login_to_register)
        }
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
        private const val KEY_FAIL_COUNT = "fail_count"
        private const val KEY_LOCK_UNTIL = "lock_until"
    }
}
