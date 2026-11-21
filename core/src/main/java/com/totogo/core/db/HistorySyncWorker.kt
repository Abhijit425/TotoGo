package com.totogo.core.db

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.totogo.core.firebase.FirebaseConfig
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

/**
 * Background housekeeping (WorkManager): uploads ratings captured offline and prunes old DEMO rows.
 * Never syncs DEMO rides to the backend.
 */
class HistorySyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val dao = TotoDatabase.get(applicationContext).history()
        dao.pruneDemo(System.currentTimeMillis() - 90L * 86_400_000L)
        if (!FirebaseConfig.isConfigured(applicationContext) || FirebaseConfig.uid() == null) return Result.success()
        val pending = dao.unsynced().filter { !it.isDemo && it.rating > 0 }
        return try {
            val db = FirebaseFirestore.getInstance()
            pending.forEach { r ->
                db.collection("feedbackQueue").document(r.rideId)
                    .set(mapOf("rideId" to r.rideId, "stars" to r.rating, "feedback" to r.feedback, "uid" to FirebaseConfig.uid()), SetOptions.merge()).await()
            }
            if (pending.isNotEmpty()) dao.markSynced(pending.map { it.rideId })
            Result.success()
        } catch (e: Exception) { Result.retry() }
    }

    companion object {
        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<HistorySyncWorker>(12, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("totogo-history-sync", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
