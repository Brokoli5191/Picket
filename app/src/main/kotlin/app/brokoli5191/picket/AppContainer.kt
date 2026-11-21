package app.brokoli5191.picket

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.datastore.preferences.preferencesDataStore
import app.brokoli5191.picket.data.ActionsRepository
import app.brokoli5191.picket.data.AppDetailRepository
import app.brokoli5191.picket.data.PackageCatalog
import app.brokoli5191.picket.data.WakeupsRepository
import app.brokoli5191.picket.data.WatchersRepository
import app.brokoli5191.picket.data.journal.JournalDatabase
import app.brokoli5191.picket.data.settings.SettingsRepository
import app.brokoli5191.picket.privileged.client.PrivilegedClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

private val Context.settingsStore by preferencesDataStore(name = "settings")

/**
 * Manual dependency injection: one instance per process, created in [PicketApplication.onCreate].
 * Everything that needs the application context or a singleton lives here.
 */
class AppContainer(
    context: Context,
) {
    val appContext: Context = context.applicationContext

    /** Lives as long as the process; for work that must not die with a screen. */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val packageCatalog = PackageCatalog(appContext.packageManager)

    val settingsRepository = SettingsRepository(appContext.settingsStore)

    val privilegedClient =
        PrivilegedClient(
            context = appContext,
            scope = applicationScope,
            ioDispatcher = Dispatchers.IO,
            mainDispatcher = Dispatchers.Main.immediate,
            serviceVersion = serviceVersion(appContext),
            debuggable = BuildConfig.DEBUG,
        )

    val watchersRepository = WatchersRepository { privilegedClient.listWatchers() }

    val wakeupsRepository = WakeupsRepository { windowMs -> privilegedClient.wakeups(windowMs) }

    val appDetailRepository = AppDetailRepository { pkg, userId -> privilegedClient.appDetail(pkg, userId) }

    /** Opened lazily: the first query runs off the main thread, Room refuses main-thread access. */
    val journalDatabase: JournalDatabase by lazy { JournalDatabase.create(appContext) }

    val actionsRepository: ActionsRepository by lazy { ActionsRepository(privilegedClient, journalDatabase.journal()) }

    private companion object {
        /**
         * Shizuku restarts a running UserService when this number changes. versionCode alone is not
         * enough (it stays 1 for local builds), so the install time of the APK is used.
         */
        fun serviceVersion(context: Context): Int {
            val info =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    context.packageManager.getPackageInfo(context.packageName, 0)
                }
            return ((info.lastUpdateTime / 1000) % Int.MAX_VALUE).toInt()
        }
    }
}
