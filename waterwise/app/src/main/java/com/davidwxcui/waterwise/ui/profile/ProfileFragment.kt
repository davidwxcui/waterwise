package com.davidwxcui.waterwise.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.davidwxcui.waterwise.R
import com.davidwxcui.waterwise.databinding.FragmentProfileBinding

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        render(ProfilePrefs.load(requireContext()))
        binding.btnEdit.setOnClickListener {
            findNavController().navigate(R.id.action_profile_to_edit)
        }
    }

    override fun onResume() {
        super.onResume()
        render(ProfilePrefs.load(requireContext()))
    }

    private fun render(p: Profile) {
        val initial = (p.name.firstOrNull() ?: 'U').uppercaseChar().toString()
        binding.tvAvatarInitial.text = initial

        binding.tvName.text = p.name
        binding.tvEmail.text = p.email
        binding.tvAge.text = "${p.age} years"
        binding.tvGender.text = when (p.sex) {
            Sex.MALE -> "Male"
            Sex.FEMALE -> "Female"
            else -> "—"
        }
        binding.tvHeight.text = "${p.heightCm} cm"
        binding.tvWeight.text = "${p.weightKg} kg"

        binding.tvActivity.text = when (p.activity) {
            ActivityLevel.SEDENTARY -> "Sedentary"
            ActivityLevel.LIGHT -> "Lightly Active"
            ActivityLevel.MODERATE -> "Moderately Active"
            ActivityLevel.ACTIVE -> "Active"
            ActivityLevel.VERY_ACTIVE -> "Very Active"
        }
        binding.tvActivityFreq.text = p.activityFreqLabel

        val goal = HydrationFormula.dailyGoalMl(
            p.weightKg.toFloat(), p.sex, p.age, p.activity
        )
        binding.tvGoal.text = "${goal}ml"
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
