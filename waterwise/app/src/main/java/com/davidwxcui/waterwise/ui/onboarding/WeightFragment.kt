package com.davidwxcui.waterwise.ui.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.davidwxcui.waterwise.R
import com.davidwxcui.waterwise.databinding.FragmentWeightBinding
import com.davidwxcui.waterwise.data.models.WeightUnit

class WeightFragment : Fragment() {

    private var _binding: FragmentWeightBinding? = null
    private val binding get() = _binding!!
    private val viewModel: OnboardingViewModel by activityViewModels()
    private var currentUnit = WeightUnit.KG

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWeightBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Use post to ensure NumberPicker is fully laid out before setup
        binding.numberPicker.post {
            setupNumberPicker()
        }

        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.chipKg.setOnClickListener {
            currentUnit = WeightUnit.KG
            setupNumberPicker()
        }

        binding.chipLb.setOnClickListener {
            currentUnit = WeightUnit.LB
            setupNumberPicker()
        }

        binding.btnNext.setOnClickListener {
            val weight = binding.numberPicker.value.toFloat()
            viewModel.setWeight(weight, currentUnit)
            findNavController().navigate(R.id.action_weight_to_height)
        }
    }

    private fun setupNumberPicker() {
        binding.numberPicker.apply {
            when (currentUnit) {
                WeightUnit.KG -> {
                    minValue = 30
                    maxValue = 200
                    value = 70
                }
                WeightUnit.LB -> {
                    minValue = 66
                    maxValue = 440
                    value = 154
                }
            }
            wrapSelectorWheel = false
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        }

        binding.chipKg.isChecked = currentUnit == WeightUnit.KG
        binding.chipLb.isChecked = currentUnit == WeightUnit.LB
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

