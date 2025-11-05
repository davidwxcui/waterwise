package com.davidwxcui.waterwise.ui.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.davidwxcui.waterwise.R
import com.davidwxcui.waterwise.databinding.FragmentGoalSelectionBinding
import com.davidwxcui.waterwise.data.models.PersonalGoal

class GoalSelectionFragment : Fragment() {

    private var _binding: FragmentGoalSelectionBinding? = null
    private val binding get() = _binding!!
    private val viewModel: OnboardingViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGoalSelectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnDrinkMore.setOnClickListener {
            viewModel.setPersonalGoal(PersonalGoal.DRINK_MORE_WATER)
            findNavController().navigate(R.id.action_goal_to_timeline)
        }

        binding.btnLoseWeight.setOnClickListener {
            viewModel.setPersonalGoal(PersonalGoal.LOSE_WEIGHT)
            findNavController().navigate(R.id.action_goal_to_timeline)
        }

        binding.btnShinySkin.setOnClickListener {
            viewModel.setPersonalGoal(PersonalGoal.SHINY_SKIN)
            findNavController().navigate(R.id.action_goal_to_timeline)
        }

        binding.btnHealthyLifestyle.setOnClickListener {
            viewModel.setPersonalGoal(PersonalGoal.HEALTHY_LIFESTYLE)
            findNavController().navigate(R.id.action_goal_to_timeline)
        }

        binding.btnImproveDigestion.setOnClickListener {
            viewModel.setPersonalGoal(PersonalGoal.IMPROVE_DIGESTION)
            findNavController().navigate(R.id.action_goal_to_timeline)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

