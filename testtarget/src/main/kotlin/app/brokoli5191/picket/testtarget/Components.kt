package app.brokoli5191.picket.testtarget

import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.admin.DeviceAdminReceiver
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.view.accessibility.AccessibilityEvent
import android.widget.TextView

/** Explains what this app is; with `--ez start_fgs true` it also starts the foreground service. */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply { setText(R.string.explanation) })
        if (intent.getBooleanExtra(EXTRA_START_FGS, false)) {
            startForegroundService(Intent(this, DummyForegroundService::class.java))
        }
    }

    companion object {
        const val EXTRA_START_FGS = "start_fgs"
    }
}

/** Qualifies the app for the browser role; shows nothing and closes. */
class BrowserActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
    }
}

/** Bound by the system once allowed; never looks at a notification. */
class DummyNotificationListener : NotificationListenerService()

/** Subscribes to no useful events (see accessibility_service.xml) and ignores what arrives. */
class DummyAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit
}

class DummyAdminReceiver : DeviceAdminReceiver()

/**
 * Generates the kinds of activity Picket reports: a foreground service with a type, a short partial
 * wakelock, one wakeup alarm and one job. Stops itself after [RUN_MS].
 */
class DummyForegroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW),
        )
        val notification =
            Notification
                .Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_target)
                .setContentTitle(getString(R.string.fgs_title))
                .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        val wakeLock =
            getSystemService(
                PowerManager::class.java,
            ).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG)
        wakeLock.acquire(WAKELOCK_MS)

        val alarmIntent =
            PendingIntent.getBroadcast(
                this,
                0,
                Intent(this, WakeupReceiver::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        getSystemService(AlarmManager::class.java)
            .set(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + ALARM_DELAY_MS, alarmIntent)

        getSystemService(JobScheduler::class.java).schedule(
            JobInfo
                .Builder(JOB_ID, ComponentName(this, DummyJobService::class.java))
                .setOverrideDeadline(JOB_DEADLINE_MS)
                .build(),
        )

        getMainExecutor().let { executor ->
            Thread {
                SystemClock.sleep(RUN_MS)
                executor.execute { stopSelf(startId) }
            }.start()
        }
        return START_NOT_STICKY
    }

    private companion object {
        const val CHANNEL = "checks"
        const val NOTIFICATION_ID = 1
        const val WAKELOCK_TAG = "PicketTestTarget:check"
        const val WAKELOCK_MS = 5_000L
        const val ALARM_DELAY_MS = 60_000L
        const val JOB_ID = 4242
        const val JOB_DEADLINE_MS = 1_000L
        const val RUN_MS = 120_000L
    }
}

class DummyJobService : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean = false

    override fun onStopJob(params: JobParameters?): Boolean = false
}

class WakeupReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) = Unit
}
