package com.davidwxcui.waterwise.ui.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.davidwxcui.waterwise.R
import com.davidwxcui.waterwise.databinding.FragmentGenderBinding
import com.davidwxcui.waterwise.data.models.Gender

class GenderFragment : Fragment() {

    private var _binding: FragmentGenderBinding? = null
    private val binding get() = _binding!!
    private val viewModel: OnboardingViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGenderBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnMale.setOnClickListener {
            viewModel.setGender(Gender.MALE)
            findNavController().navigate(R.id.action_gender_to_weight)
        }

        binding.btnFemale.setOnClickListener {
            viewModel.setGender(Gender.FEMALE)
            findNavController().navigate(R.id.action_gender_to_weight)
        }

        binding.btnOther.setOnClickListener {
            viewModel.setGender(Gender.OTHER)
            findNavController().navigate(R.id.action_gender_to_weight)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
