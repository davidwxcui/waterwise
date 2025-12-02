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

        setupWeightInput()

        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.chipKg.setOnClickListener {
            currentUnit = WeightUnit.KG
            updateWeightUnit()
        }

        binding.chipLb.setOnClickListener {
            currentUnit = WeightUnit.LB
            updateWeightUnit()
        }

        binding.btnNext.setOnClickListener {
            val weightText = binding.etWeight.text.toString()
            if (weightText.isNotEmpty()) {
                val weight = weightText.toFloatOrNull()
                if (weight != null && weight > 0) {
                    viewModel.setWeight(weight, currentUnit)
                    findNavController().navigate(R.id.action_weight_to_height)
                } else {
                    binding.inputLayoutWeight.error = "Please enter a valid weight"
                }
            } else {
                binding.inputLayoutWeight.error = "Please enter your weight"
            }
        }

        // Clear error when user starts typing
        binding.etWeight.addTextChangedListener {
            binding.inputLayoutWeight.error = null
        }
    }

    private fun setupWeightInput() {
        binding.etWeight.setText("70")
        binding.chipKg.isChecked = true
        binding.chipLb.isChecked = false
    }

    private fun updateWeightUnit() {
        val currentWeight = binding.etWeight.text.toString().toFloatOrNull() ?: 70f

        val newWeight = when (currentUnit) {
            WeightUnit.KG -> {
                if (binding.chipLb.isChecked) {
                    // Was LB, convert to KG
                    (currentWeight / 2.20462).toInt()
                } else {
                    currentWeight.toInt()
                }
            }
            WeightUnit.LB -> {
                if (binding.chipKg.isChecked) {
                    // Was KG, convert to LB
                    (currentWeight * 2.20462).toInt()
                } else {
                    currentWeight.toInt()
                }
            }
        }

        binding.etWeight.setText(newWeight.toString())
        binding.chipKg.isChecked = currentUnit == WeightUnit.KG
        binding.chipLb.isChecked = currentUnit == WeightUnit.LB
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
