package com.davidwxcui.waterwise.ui.friends

import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.davidwxcui.waterwise.R
import com.davidwxcui.waterwise.databinding.FragmentFriendsBinding
import com.davidwxcui.waterwise.ui.home.HomeViewModel

class FriendsFragment : Fragment() {

    private var _binding: FragmentFriendsBinding? = null
    private val binding get() = _binding!!

    private val vm: HomeViewModel by activityViewModels()

    private lateinit var adapter: FriendsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFriendsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        adapter = FriendsAdapter(
            onRemoveClick = { friend ->
                val displayName = friend.name.ifBlank { friend.uid }
                AlertDialog.Builder(requireContext())
                    .setTitle("Remove friend")
                    .setMessage("Remove $displayName from your friends list")
                    .setPositiveButton("Remove") { _, _ ->
                        vm.removeFriend(friend.uid)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )

        binding.rvFriends.layoutManager = LinearLayoutManager(requireContext())
        binding.rvFriends.adapter = adapter

        vm.friends.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            binding.tvEmptyFriends.isVisible = list.isEmpty()
        }

        binding.btnAddFriend.setOnClickListener {
            showAddFriendDialog()
        }

        binding.btnRequests.setOnClickListener {
            findNavController().navigate(R.id.friendRequestsFragment)
        }
    }

    private fun showAddFriendDialog() {
        val input = EditText(requireContext()).apply {
            hint = "UID or Email"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Add friend")
            .setView(input)
            .setPositiveButton("Send") { _, _ ->
                val q = input.text.toString().trim()
                if (q.isEmpty()) {
                    Toast.makeText(
                        requireContext(),
                        "Input cannot be empty",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setPositiveButton
                }

                vm.addFriendByQuery(q)

                Toast.makeText(
                    requireContext(),
                    "Friend request sent or added directly",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
