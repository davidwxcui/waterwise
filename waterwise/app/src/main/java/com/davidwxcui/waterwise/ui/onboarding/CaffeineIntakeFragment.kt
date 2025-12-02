package com.davidwxcui.waterwise.ui.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.davidwxcui.waterwise.R
import com.davidwxcui.waterwise.databinding.FragmentCaffeineIntakeBinding
import com.davidwxcui.waterwise.data.models.IntakeFrequency

class CaffeineIntakeFragment : Fragment() {

    private var _binding: FragmentCaffeineIntakeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: OnboardingViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCaffeineIntakeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnAlmostNever.setOnClickListener {
            viewModel.setCaffeineIntake(IntakeFrequency.ALMOST_NEVER)
            findNavController().navigate(R.id.action_caffeine_to_vegetables)
        }

        binding.btnRarely.setOnClickListener {
            viewModel.setCaffeineIntake(IntakeFrequency.RARELY)
            findNavController().navigate(R.id.action_caffeine_to_vegetables)
        }

        binding.btnRegularly.setOnClickListener {
            viewModel.setCaffeineIntake(IntakeFrequency.REGULARLY)
            findNavController().navigate(R.id.action_caffeine_to_vegetables)
        }

        binding.btnOften.setOnClickListener {
            viewModel.setCaffeineIntake(IntakeFrequency.OFTEN)
            findNavController().navigate(R.id.action_caffeine_to_vegetables)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
