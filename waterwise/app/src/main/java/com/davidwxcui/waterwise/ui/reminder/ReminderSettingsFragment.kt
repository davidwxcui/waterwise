package com.davidwxcui.waterwise.ui.reminder

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.NumberPicker
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.davidwxcui.waterwise.R
import com.davidwxcui.waterwise.data.models.ReminderMode
import com.davidwxcui.waterwise.databinding.FragmentReminderSettingsBinding
import com.davidwxcui.waterwise.databinding.LayoutCustomTimesBinding
import com.davidwxcui.waterwise.databinding.LayoutIntervalModeBinding
import com.davidwxcui.waterwise.utils.ReminderScheduler
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Fragment for configuring drink reminder settings
 */
class ReminderSettingsFragment : Fragment() {

    private var _binding: FragmentReminderSettingsBinding? = null
    private val binding get() = _binding!!

    private var _intervalBinding: LayoutIntervalModeBinding? = null
    private val intervalBinding get() = _intervalBinding!!

    private var _customTimesBinding: LayoutCustomTimesBinding? = null
    private val customTimesBinding get() = _customTimesBinding!!

    private val viewModel: ReminderSettingsViewModel by viewModels()
    private lateinit var timeAdapter: ReminderTimeAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReminderSettingsBinding.inflate(inflater, container, false)
        _intervalBinding = LayoutIntervalModeBinding.bind(binding.layoutIntervalMode.root)
        _customTimesBinding = LayoutCustomTimesBinding.bind(binding.layoutCustomTimes.root)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupBackButton()
        setupModeSelection()
        setupIntervalMode()
        setupCustomTimesMode()
        setupSaveButton()
        observeViewModel()
    }

    private fun setupBackButton() {
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupModeSelection() {
        // Set initial mode
        viewModel.settings.value?.let { settings ->
            when (settings.mode) {
                ReminderMode.INTERVAL -> binding.radioInterval.isChecked = true
                ReminderMode.CUSTOM_TIMES -> binding.radioCustomTimes.isChecked = true
            }
        }

        // Handle mode changes
        binding.radioGroupMode.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.radioInterval -> {
                    viewModel.updateMode(ReminderMode.INTERVAL)
                    showIntervalMode()
                }
                R.id.radioCustomTimes -> {
                    viewModel.updateMode(ReminderMode.CUSTOM_TIMES)
                    showCustomTimesMode()
                }
            }
        }
    }

    private fun setupIntervalMode() {
        // Setup NumberPickers
        intervalBinding.pickerHours.apply {
            minValue = 0
            maxValue = 23
            value = 1
            wrapSelectorWheel = false
        }

        intervalBinding.pickerMinutes.apply {
            minValue = 0
            maxValue = 3 // 4 values: 0, 1, 2, 3
            displayedValues = arrayOf("0", "15", "30", "45")
            value = 0
            wrapSelectorWheel = true
        }

        // Setup predefined chips
        val chipIds = listOf(
            R.id.chip30Min to 30,
            R.id.chip1Hour to 60,
            R.id.chip1_5Hours to 90,
            R.id.chip2Hours to 120,
            R.id.chip3Hours to 180
        )

        chipIds.forEach { (chipId, minutes) ->
            intervalBinding.root.findViewById<com.google.android.material.chip.Chip>(chipId)
                .setOnClickListener {
                    viewModel.updateInterval(minutes)
                    intervalBinding.layoutCustomInterval.isVisible = false
                }
        }

        // Custom chip shows NumberPicker
        intervalBinding.chipCustomInterval.setOnClickListener {
            intervalBinding.layoutCustomInterval.isVisible = true
        }

        // Update interval when NumberPicker changes
        val pickerListener = NumberPicker.OnValueChangeListener { _, _, _ ->
            updateIntervalFromPickers()
        }
        intervalBinding.pickerHours.setOnValueChangedListener(pickerListener)
        intervalBinding.pickerMinutes.setOnValueChangedListener(pickerListener)

        // Set initial interval
        viewModel.intervalMinutes.value?.let { minutes ->
            setChipForInterval(minutes)
        }
    }

    private fun updateIntervalFromPickers() {
        val hours = intervalBinding.pickerHours.value
        val minutesIndex = intervalBinding.pickerMinutes.value
        val minutes = when (minutesIndex) {
            0 -> 0
            1 -> 15
            2 -> 30
            else -> 45
        }
        val totalMinutes = hours * 60 + minutes
        if (totalMinutes >= 15) { // Minimum 15 minutes
            viewModel.updateInterval(totalMinutes)
        }
    }

    private fun setChipForInterval(minutes: Int) {
        when (minutes) {
            30 -> intervalBinding.chip30Min.isChecked = true
            60 -> intervalBinding.chip1Hour.isChecked = true
            90 -> intervalBinding.root.findViewById<com.google.android.material.chip.Chip>(R.id.chip1_5Hours).isChecked = true
            120 -> intervalBinding.chip2Hours.isChecked = true
            180 -> intervalBinding.chip3Hours.isChecked = true
            else -> {
                intervalBinding.chipCustomInterval.isChecked = true
                intervalBinding.layoutCustomInterval.isVisible = true
                // Set NumberPicker values
                val hours = minutes / 60
                val mins = minutes % 60
                intervalBinding.pickerHours.value = hours
                intervalBinding.pickerMinutes.value = when (mins) {
                    15 -> 1
                    30 -> 2
                    45 -> 3
                    else -> 0
                }
            }
        }
    }

    private fun setupCustomTimesMode() {
        // Setup RecyclerView
        timeAdapter = ReminderTimeAdapter { time ->
            // Handle delete
            viewModel.removeCustomTime(time)
        }

        customTimesBinding.recyclerCustomTimes.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = timeAdapter
        }

        // Setup Add Time button
        customTimesBinding.btnAddTime.setOnClickListener {
            showTimePickerDialog()
        }
    }

    private fun showTimePickerDialog() {
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_12H)
            .setHour(9)
            .setMinute(0)
            .setTitleText(getString(R.string.select_time))
            .build()

        picker.addOnPositiveButtonClickListener {
            val hour = picker.hour
            val minute = picker.minute
            val time = LocalTime.of(hour, minute)
            val timeString = time.format(DateTimeFormatter.ofPattern("HH:mm"))

            // Check if time already exists
            if (viewModel.timeExists(timeString)) {
                Snackbar.make(
                    binding.root,
                    getString(R.string.time_already_exists),
                    Snackbar.LENGTH_SHORT
                ).show()
            } else {
                viewModel.addCustomTime(timeString)
            }
        }

        picker.show(parentFragmentManager, "TIME_PICKER")
    }

    private fun showIntervalMode() {
        binding.layoutIntervalMode.root.isVisible = true
        binding.layoutCustomTimes.root.isVisible = false
    }

    private fun showCustomTimesMode() {
        binding.layoutIntervalMode.root.isVisible = false
        binding.layoutCustomTimes.root.isVisible = true
    }

    private fun setupSaveButton() {
        binding.btnSave.setOnClickListener {
            saveSettings()
        }
    }

    private fun saveSettings() {
        // Validate
        val settings = viewModel.settings.value ?: return

        if (settings.mode == ReminderMode.CUSTOM_TIMES && settings.customTimes.isEmpty()) {
            Snackbar.make(
                binding.root,
                getString(R.string.at_least_one_time_required),
                Snackbar.LENGTH_LONG
            ).show()
            return
        }

        // Save
        val success = viewModel.saveSettings()
        if (success) {
            // Reschedule reminders
            ReminderScheduler.scheduleReminders(requireContext())

            // Show success message
            Snackbar.make(
                binding.root,
                getString(R.string.settings_saved),
                Snackbar.LENGTH_SHORT
            ).show()

            // Navigate back
            findNavController().navigateUp()
        }
    }

    private fun observeViewModel() {
        viewModel.settings.observe(viewLifecycleOwner) { settings ->
            // Update UI based on mode
            when (settings.mode) {
                ReminderMode.INTERVAL -> showIntervalMode()
                ReminderMode.CUSTOM_TIMES -> showCustomTimesMode()
            }
        }

        viewModel.customTimes.observe(viewLifecycleOwner) { times ->
            timeAdapter.submitList(times)
            // Show/hide empty state
            customTimesBinding.tvEmptyState.isVisible = times.isEmpty()
            customTimesBinding.recyclerCustomTimes.isVisible = times.isNotEmpty()
        }

        viewModel.intervalMinutes.observe(viewLifecycleOwner) { minutes ->
            // Update chip selection if needed
            // (Already handled by chip clicks)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        _intervalBinding = null
        _customTimesBinding = null
    }
}

