package com.davidwxcui.waterwise.ui.onboarding

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.davidwxcui.waterwise.MainActivity
import com.davidwxcui.waterwise.databinding.FragmentDailyGoalResultBinding

class DailyGoalResultFragment : Fragment() {

    private var _binding: FragmentDailyGoalResultBinding? = null
    private val binding get() = _binding!!
    private val viewModel: OnboardingViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDailyGoalResultBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Observe the daily goal
        viewModel.dailyGoalMl.observe(viewLifecycleOwner) { goalMl ->
            goalMl?.let {
                val liters = it / 1000.0
                binding.tvDailyGoal.text = String.format("%.1f L", liters)
                binding.tvDailyGoalDetail.text = viewModel.getDailyGoalSummary()
            }
        }

        binding.btnFinish.setOnClickListener {
            // Complete onboarding and save data
            viewModel.completeOnboarding()

            // Navigate to main activity
            val intent = Intent(requireContext(), MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            requireActivity().finish()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
