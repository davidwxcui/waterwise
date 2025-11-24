package com.davidwxcui.waterwise.ui.add

import androidx.lifecycle.ViewModel
import com.davidwxcui.waterwise.data.DrinkType
import com.davidwxcui.waterwise.ui.home.HomeViewModel

class AddViewModel(private val homeViewModel: HomeViewModel) : ViewModel() {

    private var selectedAmount: Int = 250 // Default 250ml
    private var selectedBeverage: String = ""

    fun setAmount(amount: Int) {
        selectedAmount = amount
    }

    fun setBeverage(beverage: String) {
        selectedBeverage = beverage
    }

    fun saveDrink() {
        if (selectedBeverage.isEmpty()) return

        val drinkType = getDrinkTypeFromString(selectedBeverage)
        homeViewModel.addDrink(drinkType, selectedAmount)
    }

    private fun getDrinkTypeFromString(beverage: String): DrinkType {
        return when (beverage.lowercase()) {
            "water" -> DrinkType.Water
            "tea" -> DrinkType.Tea
            "coffee" -> DrinkType.Coffee
            "juice" -> DrinkType.Juice
            "soda" -> DrinkType.Soda
            "milk" -> DrinkType.Milk
            "yogurt" -> DrinkType.Yogurt
            "alcohol" -> DrinkType.Alcohol
            "sparkling" -> DrinkType.Sparkling
            else -> DrinkType.Water
        }
    }
}
