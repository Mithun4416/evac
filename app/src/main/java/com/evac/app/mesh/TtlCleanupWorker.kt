package com.evac.app.mesh

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.evac.app.EvacApplication

/**
 * TtlCleanupWorker — Periodic WorkManager job that deletes expired messages.
 *
 * Runs every 30 minutes (configured via MeshConstants.TTL_CLEANUP_INTERVAL_MIN).
 * Messages older than MeshConstants.TTL_SECONDS are considered expired and removed
 * from the local Room database to keep storage lean.
 */
class TtlCleanupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "EVAC_TTL"
        const val WORK_NAME = "ttl_cleanup"
    }

    override suspend fun doWork(): Result {
        return try {
            val db = EvacApplication.database
            val dao = db.messageDao()

            val cutoff = System.currentTimeMillis() - (MeshConstants.TTL_SECONDS * 1000L)
            val deletedCount = dao.deleteExpiredMessages(cutoff)

            Log.i(TAG, "🧹 TTL cleanup: deleted $deletedCount expired message(s)")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "✗ TTL cleanup failed", e)
            Result.retry()
        }
    }
}
