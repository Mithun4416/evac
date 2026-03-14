package com.evac.app.ui.bulletin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.evac.app.R
import kotlinx.coroutines.launch

class BulletinFragment : Fragment() {

    private val viewModel: BulletinViewModel by viewModels()
    private lateinit var adapter: BulletinAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_bulletin, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = BulletinAdapter()

        val recycler = view.findViewById<RecyclerView>(R.id.recyclerBulletins)
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        observeBulletins()
    }

    private fun observeBulletins() {
        lifecycleScope.launch {
            viewModel.bulletinsAndAcks.collect { messages ->
                adapter.submitList(messages)
            }
        }
    }
}