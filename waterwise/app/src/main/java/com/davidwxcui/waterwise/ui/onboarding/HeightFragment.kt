package com.davidwxcui.waterwise.ui.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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

        // Use post to ensure NumberPickers are fully laid out before setup
        binding.numberPickerCm.post {
            setupNumberPickers()
        }

        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.chipCm.setOnClickListener {
            currentUnit = HeightUnit.CM
            setupNumberPickers()
        }

        binding.chipFeet.setOnClickListener {
            currentUnit = HeightUnit.FEET_INCHES
            setupNumberPickers()
        }

        binding.btnNext.setOnClickListener {
            val height = when (currentUnit) {
                HeightUnit.CM -> binding.numberPickerCm.value.toFloat()
                HeightUnit.FEET_INCHES -> {
                    val feet = binding.numberPickerFeet.value
                    val inches = binding.numberPickerInches.value
                    (feet * 12 + inches) / 12f
                }
            }
            viewModel.setHeight(height, currentUnit)
            findNavController().navigate(R.id.action_height_to_training)
        }
    }

    private fun setupNumberPickers() {
        when (currentUnit) {
            HeightUnit.CM -> {
                binding.numberPickerCm.visibility = View.VISIBLE
                binding.layoutFeetInches.visibility = View.GONE
                binding.numberPickerCm.apply {
                    minValue = 120
                    maxValue = 220
                    value = 170
                    wrapSelectorWheel = false
                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                }
            }
            HeightUnit.FEET_INCHES -> {
                binding.numberPickerCm.visibility = View.GONE
                binding.layoutFeetInches.visibility = View.VISIBLE
                binding.numberPickerFeet.apply {
                    minValue = 4
                    maxValue = 7
                    value = 5
                    wrapSelectorWheel = false
                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                }
                binding.numberPickerInches.apply {
                    minValue = 0
                    maxValue = 11
                    value = 7
                    wrapSelectorWheel = false
                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                }
            }
        }

        binding.chipCm.isChecked = currentUnit == HeightUnit.CM
        binding.chipFeet.isChecked = currentUnit == HeightUnit.FEET_INCHES
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
