package app.hypertrophy.core.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.hypertrophy.core.data.network.AuthSessionStore
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: SyncRepository,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        // Never drain another account's outbox. A signed-out worker simply
        // waits for the next scheduled run after sign-in.
        val userId = AuthSessionStore.userId ?: return Result.success()
        return runCatching {
            repository.syncPending(userId)
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() },
        )
    }

    companion object {
        const val UNIQUE_WORK_NAME = "hypertrophy-sync"
    }
}
