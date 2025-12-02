package com.davidwxcui.waterwise.ui.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.davidwxcui.waterwise.R
import com.davidwxcui.waterwise.databinding.FragmentHeightBinding
import com.davidwxcui.waterwise.data.models.HeightUnit

class HeightFragment : Fragment() {

    private var _binding: FragmentHeightBinding? = null
    private val binding get() = _binding!!
    private val viewModel: OnboardingViewModel by activityViewModels()
    private var currentUnit = HeightUnit.CM

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHeightBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupHeightInput()

        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.chipCm.setOnClickListener {
            currentUnit = HeightUnit.CM
            updateHeightUnit()
        }

        binding.chipFeet.setOnClickListener {
            currentUnit = HeightUnit.FEET_INCHES
            updateHeightUnit()
        }

        binding.btnNext.setOnClickListener {
            if (validateAndSaveHeight()) {
                findNavController().navigate(R.id.action_height_to_training)
            }
        }

        // Clear errors when user starts typing
        binding.etHeightCm.addTextChangedListener {
            binding.inputLayoutHeightCm.error = null
        }
        binding.etFeet.addTextChangedListener {
            binding.inputLayoutFeet.error = null
        }
        binding.etInches.addTextChangedListener {
            binding.inputLayoutInches.error = null
        }
    }

    private fun setupHeightInput() {
        binding.etHeightCm.setText("170")
        binding.etFeet.setText("5")
        binding.etInches.setText("7")

        binding.inputLayoutHeightCm.visibility = View.VISIBLE
        binding.layoutFeetInches.visibility = View.GONE

        binding.chipCm.isChecked = true
        binding.chipFeet.isChecked = false
    }

    private fun updateHeightUnit() {
        when (currentUnit) {
            HeightUnit.CM -> {
                binding.inputLayoutHeightCm.visibility = View.VISIBLE
                binding.layoutFeetInches.visibility = View.GONE

                // Convert from feet/inches if switching from imperial
                if (!binding.chipCm.isChecked) {
                    val feet = binding.etFeet.text.toString().toIntOrNull() ?: 5
                    val inches = binding.etInches.text.toString().toIntOrNull() ?: 7
                    val totalInches = feet * 12 + inches
                    val cm = (totalInches * 2.54).toInt()
                    binding.etHeightCm.setText(cm.toString())
                }
            }
            HeightUnit.FEET_INCHES -> {
                binding.inputLayoutHeightCm.visibility = View.GONE
                binding.layoutFeetInches.visibility = View.VISIBLE

                // Convert from cm if switching from metric
                if (!binding.chipFeet.isChecked) {
                    val cm = binding.etHeightCm.text.toString().toIntOrNull() ?: 170
                    val totalInches = (cm / 2.54).toInt()
                    val feet = totalInches / 12
                    val inches = totalInches % 12
                    binding.etFeet.setText(feet.toString())
                    binding.etInches.setText(inches.toString())
                }
            }
        }

        binding.chipCm.isChecked = currentUnit == HeightUnit.CM
        binding.chipFeet.isChecked = currentUnit == HeightUnit.FEET_INCHES
    }

    private fun validateAndSaveHeight(): Boolean {
        return when (currentUnit) {
            HeightUnit.CM -> {
                val heightText = binding.etHeightCm.text.toString()
                if (heightText.isEmpty()) {
                    binding.inputLayoutHeightCm.error = "Please enter your height"
                    false
                } else {
                    val height = heightText.toFloatOrNull()
                    if (height == null || height < 120 || height > 250) {
                        binding.inputLayoutHeightCm.error = "Please enter a valid height (120-250 cm)"
                        false
                    } else {
                        viewModel.setHeight(height, currentUnit)
                        true
                    }
                }
            }
            HeightUnit.FEET_INCHES -> {
                val feetText = binding.etFeet.text.toString()
                val inchesText = binding.etInches.text.toString()

                var isValid = true

                if (feetText.isEmpty()) {
                    binding.inputLayoutFeet.error = "Enter feet"
                    isValid = false
                }
                if (inchesText.isEmpty()) {
                    binding.inputLayoutInches.error = "Enter inches"
                    isValid = false
                }

                if (isValid) {
                    val feet = feetText.toIntOrNull()
                    val inches = inchesText.toIntOrNull()

                    if (feet == null || feet < 4 || feet > 7) {
                        binding.inputLayoutFeet.error = "Valid range: 4-7 feet"
                        isValid = false
                    }
                    if (inches == null || inches < 0 || inches > 11) {
                        binding.inputLayoutInches.error = "Valid range: 0-11 inches"
                        isValid = false
                    }

                    if (isValid && feet != null && inches != null) {
                        val heightInFeet = feet + (inches / 12f)
                        viewModel.setHeight(heightInFeet, currentUnit)
                        return true
                    }
                }
                false
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
