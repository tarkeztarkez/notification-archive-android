package com.android.alftendev.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object SyncScheduler {
    private const val IMMEDIATE_WORK = "notification-archive-sync"
    private const val PERIODIC_WORK = "notification-archive-periodic-sync"

    private fun constraints(): Constraints = Constraints.Builder()
        .setRequiredNetworkType(if (SyncPreferences.wifiOnly()) NetworkType.UNMETERED else NetworkType.CONNECTED)
        .build()

    fun enqueue(context: Context) {
        if (!SyncPreferences.isEnabled()) return
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(IMMEDIATE_WORK, ExistingWorkPolicy.KEEP, request)
    }

    fun enqueueManual(context: Context) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(constraints()).build()
        WorkManager.getInstance(context).enqueueUniqueWork(IMMEDIATE_WORK, ExistingWorkPolicy.REPLACE, request)
    }

    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints()).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK, ExistingPeriodicWorkPolicy.UPDATE, request
        )
    }
}

