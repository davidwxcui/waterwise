package com.davidwxcui.waterwise.ui.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.davidwxcui.waterwise.R
import com.davidwxcui.waterwise.databinding.FragmentTrainingFrequencyBinding
import com.davidwxcui.waterwise.data.models.TrainingFrequency

class TrainingFrequencyFragment : Fragment() {

    private var _binding: FragmentTrainingFrequencyBinding? = null
    private val binding get() = _binding!!
    private val viewModel: OnboardingViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTrainingFrequencyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.btn01Day.setOnClickListener {
            viewModel.setTrainingFrequency(TrainingFrequency.NONE)
            findNavController().navigate(R.id.action_training_to_caffeine)
        }

        binding.btn23Days.setOnClickListener {
            viewModel.setTrainingFrequency(TrainingFrequency.LOW)
            findNavController().navigate(R.id.action_training_to_caffeine)
        }

        binding.btn45Days.setOnClickListener {
            viewModel.setTrainingFrequency(TrainingFrequency.MEDIUM)
            findNavController().navigate(R.id.action_training_to_caffeine)
        }

        binding.btn67Days.setOnClickListener {
            viewModel.setTrainingFrequency(TrainingFrequency.HIGH)
            findNavController().navigate(R.id.action_training_to_caffeine)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
