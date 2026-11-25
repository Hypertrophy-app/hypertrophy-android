package app.hypertrophy

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import app.hypertrophy.auth.SessionStore
import app.hypertrophy.core.data.sync.SyncScheduler
import com.clerk.api.Clerk
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class HypertrophyApplication : Application(), Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var syncScheduler: SyncScheduler

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        val publishableKey = BuildConfig.CLERK_PUBLISHABLE_KEY
        if (publishableKey.isNotBlank()) {
            Clerk.initialize(this, publishableKey = publishableKey)
            SessionStore.start()
        }
        syncScheduler.ensureScheduled()
    }
}
