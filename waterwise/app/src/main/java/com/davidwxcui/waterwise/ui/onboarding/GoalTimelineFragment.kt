package com.davidwxcui.waterwise.ui.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.davidwxcui.waterwise.R
import com.davidwxcui.waterwise.databinding.FragmentGoalTimelineBinding
import com.davidwxcui.waterwise.data.models.GoalTimeline

class GoalTimelineFragment : Fragment() {

    private var _binding: FragmentGoalTimelineBinding? = null
    private val binding get() = _binding!!
    private val viewModel: OnboardingViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGoalTimelineBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btn3Days.setOnClickListener {
            viewModel.setGoalTimeline(GoalTimeline.THREE_DAYS)
            viewModel.calculateDailyGoal()
            findNavController().navigate(R.id.action_timeline_to_result)
        }

        binding.btn7Days.setOnClickListener {
            viewModel.setGoalTimeline(GoalTimeline.SEVEN_DAYS)
            viewModel.calculateDailyGoal()
            findNavController().navigate(R.id.action_timeline_to_result)
        }

        binding.btn14Days.setOnClickListener {
            viewModel.setGoalTimeline(GoalTimeline.FOURTEEN_DAYS)
            viewModel.calculateDailyGoal()
            findNavController().navigate(R.id.action_timeline_to_result)
        }

        binding.btn30Days.setOnClickListener {
            viewModel.setGoalTimeline(GoalTimeline.THIRTY_DAYS)
            viewModel.calculateDailyGoal()
            findNavController().navigate(R.id.action_timeline_to_result)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

