package com.evac.app.db

import androidx.room.Database
import androidx.room.RoomDatabase

// Room database
@Database(entities = [MessageEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
}
