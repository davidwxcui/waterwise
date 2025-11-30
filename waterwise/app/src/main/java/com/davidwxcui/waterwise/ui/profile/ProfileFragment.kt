package com.davidwxcui.waterwise.ui.profile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.davidwxcui.waterwise.R
import com.davidwxcui.waterwise.databinding.FragmentProfileBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        render(profileForUi())
        setupAuthButtons()
        binding.btnResetGoal.setOnClickListener { resetDailyGoal() }
    }

    override fun onResume() {
        super.onResume()
        render(profileForUi())
        setupAuthButtons()
    }

    private fun setupAuthButtons() {
        val loggedIn = LocalAuthRepository.isLoggedIn(requireContext())

        if (!loggedIn) {
            binding.btnEdit.text = "Login"
            binding.btnEdit.setOnClickListener {
                findNavController().navigate(R.id.action_profile_to_login)
            }
        } else {
            binding.btnEdit.text = "Edit Profile"
            binding.btnEdit.setOnClickListener {
                findNavController().navigate(R.id.action_profile_to_edit)
            }
        }

        binding.btnLogout.isVisible = loggedIn
        binding.btnLogout.setOnClickListener {
            LocalAuthRepository.logout(requireContext())
            FirebaseAuthRepository.logout()
            render(profileForUi())
            setupAuthButtons()
            Snackbar.make(binding.root, "Logged out", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun profileForUi(): Profile {
        val real = ProfilePrefs.load(requireContext())
        return if (LocalAuthRepository.isLoggedIn(requireContext())) real
        else real.copy(
            name = "Guest",
            email = "",
            age = 0,
            sex = Sex.UNSPECIFIED,
            heightCm = 0,
            weightKg = 0,
            activity = ActivityLevel.SEDENTARY,
            activityFreqLabel = "",
            avatarUri = null
        )
    }

    private fun render(p: Profile) {
        binding.tvAvatarInitial.text =
            (p.name.firstOrNull() ?: 'U').uppercaseChar().toString()

        binding.tvName.text = p.name
        binding.tvEmail.text = p.email
        binding.tvAge.text = if (p.age == 0) "—" else "${p.age} years"
        binding.tvGender.text = when (p.sex) {
            Sex.MALE -> "Male"
            Sex.FEMALE -> "Female"
            else -> "—"
        }
        binding.tvHeight.text = if (p.heightCm == 0) "—" else "${p.heightCm} cm"
        binding.tvWeight.text = if (p.weightKg == 0) "—" else "${p.weightKg} kg"

        binding.tvActivity.text = when (p.activity) {
            ActivityLevel.SEDENTARY -> "Sedentary"
            ActivityLevel.LIGHT -> "Lightly Active"
            ActivityLevel.MODERATE -> "Moderately Active"
            ActivityLevel.ACTIVE -> "Active"
            ActivityLevel.VERY_ACTIVE -> "Very Active"
        }
        binding.tvActivityFreq.text = p.activityFreqLabel.ifBlank { "—" }

        val goal = HydrationFormula.dailyGoalMl(
            p.weightKg.toFloat(), p.sex, p.age, p.activity
        )
        binding.tvGoal.text = if (p.weightKg == 0) "—" else "${goal}ml"

        val uid = if (LocalAuthRepository.isLoggedIn(requireContext()))
            LocalAuthRepository.getUid(requireContext())
        else null

        binding.tvUid.text = uid?.let { "UID: $it" } ?: "UID: —"
        binding.btnCopyUid.isVisible = !uid.isNullOrBlank()
        binding.btnCopyUid.setOnClickListener {
            val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("uid", uid))
            Snackbar.make(binding.root, "UID copied", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun resetDailyGoal() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Reset Daily Goal")
            .setMessage("This will restart onboarding and recalculate your daily goal.\n\nContinue?")
            .setPositiveButton("Reset") { _, _ ->
                val onboardingPrefs =
                    com.davidwxcui.waterwise.data.OnboardingPreferences(requireContext())
                onboardingPrefs.setOnboardingCompleted(false)

                val intent = Intent(
                    requireActivity(),
                    com.davidwxcui.waterwise.ui.onboarding.OnboardingActivity::class.java
                )
                intent.flags =
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                requireActivity().finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() { _binding = null; super.onDestroyView() }
}
