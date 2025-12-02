package com.davidwxcui.waterwise.ui.friends

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.davidwxcui.waterwise.R
import com.davidwxcui.waterwise.ui.home.HomeViewModel

// Friend Requests Page
class FriendRequestsFragment : Fragment(R.layout.fragment_friend_requests) {

    private val viewModel: HomeViewModel by activityViewModels()
    private lateinit var adapter: FriendRequestsAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btnBack = view.findViewById<ImageButton>(R.id.btnBackRequests)
        btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        val rv = view.findViewById<RecyclerView>(R.id.rvRequests)

        adapter = FriendRequestsAdapter(
            onAccept = { req ->
                viewModel.acceptFriendRequest(req)
            },
            onDecline = { req ->
                viewModel.declineFriendRequest(req)
            }
        )

        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        viewModel.friendRequests.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
        }
    }
}

