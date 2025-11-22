package com.davidwxcui.waterwise.ui.add

import android.os.Bundle
import android.widget.Toast
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.davidwxcui.waterwise.databinding.FragmentAddBinding
import com.davidwxcui.waterwise.ui.home.HomeViewModel
import android.graphics.Color
import android.widget.EditText
import android.widget.NumberPicker

class AddFragment : Fragment() {

    private var _binding: FragmentAddBinding? = null
    private val binding get() = _binding!!

    private val homeViewModel: HomeViewModel by activityViewModels()
    private var currentAmount: Int = 250   // always stored in ml

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        currentAmount = 250
        setupDisplayNumberPicker()
        setupAmountButtons()
        setupBeverageCards()
    }

    /** Make selected item text larger */
    private fun setNumberPickerTextSize(picker: NumberPicker, size: Float) {
        for (i in 0 until picker.childCount) {
            val child = picker.getChildAt(i)
            if (child is EditText) {
                child.textSize = size
                child.setTextColor(Color.BLACK)
            }
        }
    }

    /** NumberPicker: 50 ml → 1000 ml (steps of 10 ml) */
    private fun setupDisplayNumberPicker() {
        val minValue = 5      // 50 ml
        val maxValue = 100    // 1000 ml

        binding.amountDisplayPicker.apply {
            setMinValue(minValue)
            setMaxValue(maxValue)

            value = currentAmount / 10    // <<< correct mapping

            wrapSelectorWheel = false

            displayedValues = (minValue..maxValue)
                .map { "${it * 10}ml" }
                .toTypedArray()

            setFormatter { value -> "${value * 10}ml" }

            setOnScrollListener { _, _ ->
                setNumberPickerTextSize(this, 26f)
            }

            setOnValueChangedListener { _, _, newVal ->
                currentAmount = newVal * 10
                updateQuickButtons(currentAmount)
            }

            post {
                setNumberPickerTextSize(this, 26f)
            }
        }
    }

    /** Buttons: 100 / 250 / 500 / 750 ml */
    private fun setupAmountButtons() {
        val amounts = listOf(
            binding.btn100ml to 100,
            binding.btn250ml to 250,
            binding.btn500ml to 500,
            binding.btn750ml to 750
        )

        amounts.forEach { (button, amount) ->
            button.setOnClickListener {
                currentAmount = amount
                updateQuickButtons(amount)

                // *** FIXED: correct NumberPicker mapping ***
                binding.amountDisplayPicker.value = amount / 10
            }
        }
    }

    /** Highlight correct quick button */
    private fun updateQuickButtons(selectedAmount: Int) {
        val buttons = listOf(
            binding.btn100ml to 100,
            binding.btn250ml to 250,
            binding.btn500ml to 500,
            binding.btn750ml to 750
        )

        buttons.forEach { (button, amount) ->
            if (amount == selectedAmount) {
                button.setBackgroundColor(Color.parseColor("#00838F"))
                button.setTextColor(Color.WHITE)
            } else {
                button.setBackgroundColor(Color.parseColor("#F5F5F5"))
                button.setTextColor(Color.parseColor("#666666"))
            }
        }
    }

    private fun setupBeverageCards() {
        val beverages = listOf(
            binding.waterCard to "Water",
            binding.teaCard to "Tea",
            binding.coffeeCard to "Coffee",
            binding.juiceCard to "Juice",
            binding.milkCard to "Milk",
            binding.sodaCard to "Soda",
            binding.yogurtCard to "Yogurt",
            binding.alcoholCard to "Alcohol",
            binding.sparklingCard to "Sparkling"
        )

        beverages.forEach { (card, name) ->
            card.setOnClickListener {
                saveDrink(name)
            }
        }
    }

    private fun saveDrink(beverageName: String) {
        val drinkType = getDrinkTypeFromString(beverageName)

        homeViewModel.addDrink(drinkType, currentAmount)
        Toast.makeText(requireContext(), "Added $currentAmount ml of $beverageName", Toast.LENGTH_SHORT).show()

        updateQuickButtons(currentAmount)
    }

    private fun getDrinkTypeFromString(bev: String): com.davidwxcui.waterwise.data.DrinkType {
        return when (bev.lowercase()) {
            "water" -> com.davidwxcui.waterwise.data.DrinkType.Water
            "tea" -> com.davidwxcui.waterwise.data.DrinkType.Tea
            "coffee" -> com.davidwxcui.waterwise.data.DrinkType.Coffee
            "juice" -> com.davidwxcui.waterwise.data.DrinkType.Juice
            "soda" -> com.davidwxcui.waterwise.data.DrinkType.Soda
            "milk" -> com.davidwxcui.waterwise.data.DrinkType.Milk
            "yogurt" -> com.davidwxcui.waterwise.data.DrinkType.Yogurt
            "alcohol" -> com.davidwxcui.waterwise.data.DrinkType.Alcohol
            "sparkling" -> com.davidwxcui.waterwise.data.DrinkType.Sparkling
            else -> com.davidwxcui.waterwise.data.DrinkType.Water
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
