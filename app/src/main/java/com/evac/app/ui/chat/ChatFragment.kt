package com.evac.app.ui.chat

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.evac.app.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.textfield.TextInputEditText

class ChatFragment : Fragment() {

    data class ChatMessage(val text: String, val isUser: Boolean, val title: String? = null)

    private val messages = mutableListOf<ChatMessage>()
    private lateinit var adapter: ChatAdapter
    private lateinit var recyclerView: RecyclerView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_chat, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.rv_chat)
        val etInput = view.findViewById<TextInputEditText>(R.id.et_chat_input)
        val btnSend = view.findViewById<MaterialButton>(R.id.btn_send)

        adapter = ChatAdapter(messages)
        recyclerView.layoutManager = LinearLayoutManager(context).apply { stackFromEnd = true }
        recyclerView.adapter = adapter

        // Welcome message
        addBotMessage("Welcome to Evac Bot!", "I'm your offline assistant - ask me about survival, general knowledge, math, trivia, or anything!\n\nWater  |  First Aid  |  Earthquake\nFlood  |  Fire  |  CPR\nTrivia  |  Math  |  General\n\nType anything or tap a quick action below!")

        btnSend.setOnClickListener {
            val text = etInput.text?.toString()?.trim() ?: ""
            if (text.isNotEmpty()) {
                sendMessage(text)
                etInput.text?.clear()
            }
        }

        etInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                btnSend.performClick()
                true
            } else false
        }

        // Quick action chips
        val quickActions = view.findViewById<LinearLayout>(R.id.quick_actions)
        for (i in 0 until quickActions.childCount) {
            val chip = quickActions.getChildAt(i)
            if (chip is Chip) {
                chip.setOnClickListener {
                    val topic = chip.text.toString().replace(Regex("[^\\w\\s]"), "").trim()
                    sendMessage(topic)
                }
            }
        }
    }

    private fun sendMessage(text: String) {
        messages.add(ChatMessage(text, isUser = true))
        adapter.notifyItemInserted(messages.size - 1)
        recyclerView.scrollToPosition(messages.size - 1)

        // Bot response with small delay for feel
        recyclerView.postDelayed({
            val response = SurvivalBot.getResponse(text)
            addBotMessage(response.title, response.body)
        }, 400)
    }

    private fun addBotMessage(title: String, body: String) {
        messages.add(ChatMessage(body, isUser = false, title = title))
        adapter.notifyItemInserted(messages.size - 1)
        recyclerView.scrollToPosition(messages.size - 1)
    }

    // Simple inline adapter
    inner class ChatAdapter(private val items: List<ChatMessage>) : RecyclerView.Adapter<ChatAdapter.VH>() {

        inner class VH(val container: LinearLayout) : RecyclerView.ViewHolder(container)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val container = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = 12 }
            }
            return VH(container)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val msg = items[position]
            holder.container.removeAllViews()

            if (msg.isUser) {
                holder.container.gravity = Gravity.END
                val tv = TextView(holder.container.context).apply {
                    text = msg.text
                    setTextColor(Color.parseColor("#FFFFFF"))
                    setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f)
                    setBackgroundColor(Color.parseColor("#1AFFFFFF"))
                    setPadding(32, 20, 32, 20)
                    val lp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { marginEnd = 8 }
                    layoutParams = lp
                }
                holder.container.addView(tv)
            } else {
                holder.container.gravity = Gravity.START
                // Title
                if (msg.title != null) {
                    val titleTv = TextView(holder.container.context).apply {
                        text = msg.title
                        setTextColor(Color.parseColor("#FFFFFF"))
                        setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15f)
                        setTypeface(null, android.graphics.Typeface.BOLD)
                        setPadding(16, 8, 16, 4)
                    }
                    holder.container.addView(titleTv)
                }
                val tv = TextView(holder.container.context).apply {
                    text = msg.text
                    setTextColor(Color.parseColor("#F0F0F5"))
                    setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13f)
                    setBackgroundColor(Color.parseColor("#BF333333"))
                    setPadding(32, 16, 32, 16)
                    setLineSpacing(4f, 1f)
                    val lp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { marginStart = 8; topMargin = 2 }
                    layoutParams = lp
                }
                holder.container.addView(tv)
            }
        }

        override fun getItemCount() = items.size
    }
}
