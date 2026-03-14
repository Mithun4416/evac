package com.evac.app.ui.bulletin

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.evac.app.db.AppDatabase
import com.evac.app.db.MessageEntity
import kotlinx.coroutines.flow.Flow

class BulletinViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)

    val bulletinsAndAcks: Flow<List<MessageEntity>> =
        database.messageDao().getBulletinsAndAcks()
}