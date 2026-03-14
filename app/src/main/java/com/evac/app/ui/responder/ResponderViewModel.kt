package com.evac.app.ui.responder

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import com.evac.app.db.AppDatabase
import com.evac.app.db.MessageEntity
import kotlinx.coroutines.flow.Flow

class ResponderViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)

    val isLoggedIn = MutableLiveData(false)

    // Hardcoded PIN for hackathon demo
    private val RESPONDER_PIN = "1234"

    fun checkPin(pin: String): Boolean {
        return if (pin == RESPONDER_PIN) {
            isLoggedIn.value = true
            true
        } else {
            false
        }
    }

    val sosMessages: Flow<List<MessageEntity>> =
        database.messageDao().getSosMessages()
}