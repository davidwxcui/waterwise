package com.davidwxcui.waterwise.ui.profile

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.davidwxcui.waterwise.R
import com.davidwxcui.waterwise.data.ReminderPreferences
import com.davidwxcui.waterwise.data.models.ReminderMode
import com.davidwxcui.waterwise.databinding.FragmentProfileBinding
import com.davidwxcui.waterwise.utils.NotificationHelper
import com.davidwxcui.waterwise.utils.ReminderScheduler
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.firestore.FirebaseFirestore

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private lateinit var reminderPrefs: ReminderPreferences

    // Notification permission launcher for Android 13+
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Permission granted, enable reminders
            reminderPrefs.setReminderEnabled(true)
            ReminderScheduler.scheduleReminders(requireContext())
            binding.switchReminder.isChecked = true
            setupReminderRow()
            Snackbar.make(binding.root, "Reminders enabled", Snackbar.LENGTH_SHORT).show()
        } else {
            // Permission denied
            binding.switchReminder.isChecked = false
            Snackbar.make(
                binding.root,
                getString(R.string.notification_permission_required),
                Snackbar.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize reminder preferences
        reminderPrefs = ReminderPreferences(requireContext())

        render(profileForUi())
        setupAuthButtons()
        setupReminderRow()
        binding.btnResetGoal.setOnClickListener { resetDailyGoal() }
    }

    override fun onResume() {
        super.onResume()
        render(profileForUi())
        setupAuthButtons()
        setupReminderRow()
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

        // Hide Reset Goal button when not logged in
        binding.btnResetGoal.isVisible = loggedIn

        // Hide Drink Reminder card when not logged in
        binding.cardReminderSection.isVisible = loggedIn
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
        showAvatar(p)

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

        // Show public Id or numeric ID
        loadPublicId()
    }

    private fun showAvatar(p: Profile) {
        val initialChar = (p.name.firstOrNull() ?: 'U').uppercaseChar()
        binding.tvAvatarInitial.text = initialChar.toString()

        val avatarStr = p.avatarUri
        if (avatarStr.isNullOrBlank()) {
            binding.ivAvatar.visibility = View.GONE
            binding.tvAvatarInitial.visibility = View.VISIBLE
            return
        }

        val uri = try {
            Uri.parse(avatarStr)
        } catch (_: Exception) {
            binding.ivAvatar.visibility = View.GONE
            binding.tvAvatarInitial.visibility = View.VISIBLE
            return
        }

        binding.ivAvatar.visibility = View.VISIBLE
        binding.tvAvatarInitial.visibility = View.GONE

        Glide.with(this)
            .load(uri)
            .circleCrop()
            .into(binding.ivAvatar)
    }

    // load user numeric id
    private fun loadPublicId() {
        val firebaseUid = FirebaseAuthRepository.currentUid()
        if (firebaseUid == null) {
            binding.tvUid.text = "ID: —"
            binding.btnCopyUid.isVisible = false
            return
        }

        val db = FirebaseFirestore.getInstance()
        db.collection("users").document(firebaseUid)
            .get()
            .addOnSuccessListener { snap ->
                if (!isAdded) return@addOnSuccessListener

                val numeric = snap.getString("numericUid")
                if (numeric.isNullOrBlank()) {
                    binding.tvUid.text = "ID: —"
                    binding.btnCopyUid.isVisible = false
                } else {
                    binding.tvUid.text = "ID: $numeric"
                    binding.btnCopyUid.isVisible = true
                    binding.btnCopyUid.setOnClickListener {
                        val cm = requireContext()
                            .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("uid", numeric))
                        Snackbar.make(binding.root, "ID copied", Snackbar.LENGTH_SHORT).show()
                    }
                }
            }
            .addOnFailureListener {
                if (!isAdded) return@addOnFailureListener
                binding.tvUid.text = "ID: —"
                binding.btnCopyUid.isVisible = false
            }
    }

    private fun setupReminderRow() {
        val settings = reminderPrefs.getReminderSettings()

        // Update switch state (without triggering listener)
        binding.switchReminder.setOnCheckedChangeListener(null)
        binding.switchReminder.isChecked = settings.isEnabled

        // Update status text
        binding.tvReminderStatus.text = when {
            !settings.isEnabled -> getString(R.string.reminder_not_set)
            settings.mode == ReminderMode.INTERVAL -> {
                val formatted = formatInterval(settings.intervalMinutes)
                getString(R.string.reminder_interval_format, formatted)
            }
            else -> getString(R.string.reminder_custom_times_format, settings.customTimes.size)
        }

        // Handle switch toggle
        binding.switchReminder.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // Check notification permission first
                if (NotificationHelper.checkNotificationPermission(requireContext())) {
                    reminderPrefs.setReminderEnabled(true)
                    ReminderScheduler.scheduleReminders(requireContext())
                    setupReminderRow() // Refresh
                } else {
                    // Request permission on Android 13+
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        // Permission not needed, just enable
                        reminderPrefs.setReminderEnabled(true)
                        ReminderScheduler.scheduleReminders(requireContext())
                        setupReminderRow() // Refresh
                    }
                }
            } else {
                // Disable reminders
                reminderPrefs.setReminderEnabled(false)
                ReminderScheduler.cancelReminders(requireContext())
                setupReminderRow() // Refresh
            }
        }

        // Handle row click - navigate to settings
        binding.layoutReminderRow.setOnClickListener {
            findNavController().navigate(R.id.action_profile_to_reminder_settings)
        }
    }

    private fun formatInterval(minutes: Int): String {
        return when {
            minutes < 60 -> "$minutes ${if (minutes == 1) "minute" else "minutes"}"
            minutes % 60 == 0 -> {
                val hours = minutes / 60
                "$hours ${if (hours == 1) "hour" else "hours"}"
            }
            else -> {
                val hours = minutes / 60
                val mins = minutes % 60
                "$hours h $mins min"
            }
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

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
