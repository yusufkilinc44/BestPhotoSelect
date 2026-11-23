package com.bestphotoselect.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.bestphotoselect.data.model.AutopilotSchedule
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutoPilotScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun schedule(schedule: AutopilotSchedule) {
        val interval = when (schedule) {
            AutopilotSchedule.DAILY -> 1L to TimeUnit.DAYS
            AutopilotSchedule.EVERY_3_DAYS -> 3L to TimeUnit.DAYS
            AutopilotSchedule.WEEKLY -> 7L to TimeUnit.DAYS
        }
        val request = PeriodicWorkRequestBuilder<AutoPilotWorker>(interval.first, interval.second)
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request
        )
    }

    /**
     * Süresi dolan otomatik taramanın devamını hemen kuyruğa ekler. Tarama
     * önbelleği parça parça yazdığı için devam işi kaldığı yerden sürer.
     */
    fun enqueueContinuation(chain: Int) {
        val request = OneTimeWorkRequestBuilder<AutoPilotWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .setInputData(workDataOf(AutoPilotWorker.KEY_CHAIN to chain))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            CONTINUE_WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request
        )
    }

    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        WorkManager.getInstance(context).cancelUniqueWork(CONTINUE_WORK_NAME)
    }

    companion object {
        private const val WORK_NAME = "autopilot_scan"
        private const val CONTINUE_WORK_NAME = "autopilot_scan_continue"
    }
}
