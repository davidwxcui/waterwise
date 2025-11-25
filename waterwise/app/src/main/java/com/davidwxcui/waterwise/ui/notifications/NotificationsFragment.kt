package com.davidwxcui.waterwise.ui.notifications

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.davidwxcui.waterwise.databinding.FragmentNotificationsBinding
import kotlinx.coroutines.launch
import java.util.*

class NotificationsFragment : Fragment() {

    private var _binding: FragmentNotificationsBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: NotificationsViewModel
    private lateinit var adapter: EventAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotificationsBinding.inflate(inflater, container, false)
        val root: View = binding.root

        // Initialize ViewModel
        viewModel = ViewModelProvider(this).get(NotificationsViewModel::class.java)

        // Setup RecyclerView
        adapter = EventAdapter { event ->
            // Delete callback
            viewModel.deleteEvent(event)
            Toast.makeText(requireContext(), "Event deleted", Toast.LENGTH_SHORT).show()
        }
        binding.eventsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.eventsRecyclerView.adapter = adapter

        // Observe events from ViewModel
        lifecycleScope.launch {
            viewModel.allEvents.collect { events ->
                val sorted = events.sortedBy { it.daysUntil }
                adapter.submitList(sorted)
            }
        }

        // Add New Event Button
        binding.addEventButton.setOnClickListener {
            showAddEventDialog()
        }

        return root
    }

    private fun showAddEventDialog() {
        val eventTitleInput = EditText(requireContext()).apply {
            hint = "Enter event title (e.g., Marathon Race)"
            setPadding(32, 32, 32, 32)
        }

        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Create New Event")
            .setView(eventTitleInput)
            .setPositiveButton("Next") { _, _ ->
                val eventTitle = eventTitleInput.text.toString().trim()
                if (eventTitle.isNotEmpty()) {
                    showDatePicker(eventTitle)
                } else {
                    Toast.makeText(requireContext(), "Please enter event title", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDatePicker(eventTitle: String) {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        // Disable past dates
        val datePickerDialog = DatePickerDialog(requireContext(), { _, selectedYear, selectedMonth, selectedDay ->
            val selectedDate = String.format(
                "%04d-%02d-%02d",
                selectedYear,
                selectedMonth + 1,
                selectedDay
            )
            viewModel.createEventWithAI(eventTitle, selectedDate)
            Toast.makeText(
                requireContext(),
                "Event '$eventTitle' created for $selectedDate",
                Toast.LENGTH_SHORT
            ).show()
        }, year, month, day)

        datePickerDialog.datePicker.minDate = calendar.timeInMillis
        datePickerDialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}