package com.evac.app.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.evac.app.db.AppDatabase
import com.evac.app.repository.SafeSpotRepository

class SafeSpotSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "SafeSpotSyncWorker"
        private const val TAG = "SafeSpotSyncWorker"
    }

    override suspend fun doWork(): Result {
        Log.i(TAG, "Starting background sync of SafeSpots...")

        return try {
            val database = AppDatabase.getInstance(applicationContext)
            val repo = SafeSpotRepository(database.safeSpotDao())
            val manager = SafeSpotSyncManager(repo)

            when (val result = manager.sync()) {
                is SafeSpotSyncManager.SyncResult.Success -> {
                    Log.i(TAG, "Sync successful! Fetched: ${result.totalFetched}, Written: ${result.written}")
                    Result.success()
                }
                is SafeSpotSyncManager.SyncResult.Error -> {
                    Log.e(TAG, "Sync failed: ${result.message}")
                    Result.retry()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during sync", e)
            Result.retry()
        }
    }
}
