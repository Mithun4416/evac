package com.evac.app.ui.map

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.evac.app.db.AppDatabase
import com.evac.app.db.MessageEntity
import kotlinx.coroutines.flow.Flow

class MapViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)

    val sosMessages: Flow<List<MessageEntity>> =
        database.messageDao().getSosMessages()
}