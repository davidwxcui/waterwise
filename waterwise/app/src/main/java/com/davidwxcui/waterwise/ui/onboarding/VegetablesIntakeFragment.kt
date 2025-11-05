package com.davidwxcui.waterwise.ui.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.davidwxcui.waterwise.R
import com.davidwxcui.waterwise.databinding.FragmentVegetablesIntakeBinding
import com.davidwxcui.waterwise.data.models.IntakeFrequency

class VegetablesIntakeFragment : Fragment() {

    private var _binding: FragmentVegetablesIntakeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: OnboardingViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVegetablesIntakeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnRarely.setOnClickListener {
            viewModel.setVegetablesIntake(IntakeFrequency.RARELY)
            findNavController().navigate(R.id.action_vegetables_to_goal)
        }

        binding.btnRegularly.setOnClickListener {
            viewModel.setVegetablesIntake(IntakeFrequency.REGULARLY)
            findNavController().navigate(R.id.action_vegetables_to_goal)
        }

        binding.btnOften.setOnClickListener {
            viewModel.setVegetablesIntake(IntakeFrequency.OFTEN)
            findNavController().navigate(R.id.action_vegetables_to_goal)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

