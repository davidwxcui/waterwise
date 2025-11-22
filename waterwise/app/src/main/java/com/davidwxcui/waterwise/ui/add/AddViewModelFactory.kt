package com.davidwxcui.waterwise.ui.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.davidwxcui.waterwise.ui.home.HomeViewModel

class AddViewModelFactory(private val homeViewModel: HomeViewModel) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AddViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AddViewModel(homeViewModel) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}