package app.brokoli5191.picket.privileged.client

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.RemoteException
import android.os.SystemClock
import android.util.Log
import app.brokoli5191.picket.core.action.Action
import app.brokoli5191.picket.core.action.ActionOutcome
import app.brokoli5191.picket.core.action.ActionPlan
import app.brokoli5191.picket.core.ipc.PayloadCodec
import app.brokoli5191.picket.core.ipc.Reply
import app.brokoli5191.picket.core.ipc.ServiceError
import app.brokoli5191.picket.core.model.AppDetail
import app.brokoli5191.picket.core.model.ProcessSnapshot
import app.brokoli5191.picket.core.model.WakeupReport
import app.brokoli5191.picket.core.model.WatchersSnapshot
import app.brokoli5191.picket.core.process.Uids
import app.brokoli5191.picket.privileged.IPicketService
import app.brokoli5191.picket.privileged.service.PicketUserService
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import rikka.shizuku.Shizuku
import rikka.sui.Sui

/**
 * App-side connection to [PicketUserService] through Shizuku: tracks the Shizuku lifecycle,
 * requests permission, binds the UserService and offers typed, suspending calls.
 */
class PrivilegedClient(
    context: Context,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    /** Lifecycle bookkeeping (binding, state) happens on this dispatcher only. */
    private val mainDispatcher: CoroutineDispatcher,
    /** Restarts a running service whose code is older than the installed APK. */
    serviceVersion: Int,
    debuggable: Boolean,
) {
    private val appContext = context.applicationContext
    private val packageManager = appContext.packageManager

    private val mutableState = MutableStateFlow<PrivilegedState>(PrivilegedState.Checking)
    val state: StateFlow<PrivilegedState> = mutableState.asStateFlow()

    /** Timing of the most recent call, for the debug overlay. */
    private val mutableLastCall = MutableStateFlow<CallTiming?>(null)
    val lastCall: StateFlow<CallTiming?> = mutableLastCall.asStateFlow()

    @Volatile
    private var service: IPicketService? = null

    @Volatile
    private var bindRequested = false

    /** Main-thread bookkeeping for the connect watchdog. */
    private var watchdog: Job? = null
    private var automaticRetries = 0

    /** Counts bind attempts, so a late handshake of an abandoned attempt is ignored. */
    private var attempt = 0

    private val args =
        Shizuku
            .UserServiceArgs(ComponentName(appContext.packageName, PicketUserService::class.java.name))
            .daemon(false)
            .processNameSuffix(PROCESS_SUFFIX)
            .debuggable(debuggable)
            .version(serviceVersion)

    private val binderReceived = Shizuku.OnBinderReceivedListener { evaluate() }
    private val binderDead =
        Shizuku.OnBinderDeadListener {
            watchdog?.cancel()
            service = null
            bindRequested = false
            evaluate()
        }
    private val permissionResult =
        Shizuku.OnRequestPermissionResultListener { requestCode, _ ->
            if (requestCode == PERMISSION_REQUEST_CODE) evaluate()
        }

    private val connection =
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName,
                binder: IBinder?,
            ) {
                if (!bindRequested) return
                if (binder == null || !binder.pingBinder()) {
                    // A stale binder of a helper that is going away; the watchdog retries.
                    Log.w(TAG, "Ignoring a dead binder from onServiceConnected")
                    return
                }
                val candidate = IPicketService.Stub.asInterface(binder)
                val token = attempt
                scope.launch(ioDispatcher) {
                    val result = handshake(candidate)
                    withContext(mainDispatcher) { finishConnect(token, candidate, result) }
                }
            }

            override fun onServiceDisconnected(name: ComponentName) {
                service = null
                bindRequested = false
                evaluate()
            }
        }

    fun start() {
        Shizuku.addBinderReceivedListenerSticky(binderReceived)
        Shizuku.addBinderDeadListener(binderDead)
        Shizuku.addRequestPermissionResultListener(permissionResult)
        evaluate()
    }

    /** Re-checks Shizuku and binds if possible. Safe to call often (e.g. on every resume). */
    fun evaluate() {
        if (!Shizuku.pingBinder()) {
            service = null
            bindRequested = false
            mutableState.value = if (isShizukuInstalled()) PrivilegedState.NotRunning else PrivilegedState.NotInstalled
            return
        }
        if (Shizuku.isPreV11()) {
            mutableState.value = PrivilegedState.Unsupported(Shizuku.getVersion())
            return
        }
        when {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> bind()
            Shizuku.shouldShowRequestPermissionRationale() -> mutableState.value = PrivilegedState.PermissionDenied
            else -> mutableState.value = PrivilegedState.PermissionRequired
        }
    }

    fun requestPermission() {
        if (Shizuku.pingBinder() && !Shizuku.isPreV11()) Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
    }

    /** Stops the privileged process (it is restarted on the next bind). */
    fun shutdown() {
        dropBinding()
        evaluate()
    }

    /**
     * Throws away whatever binding exists, stuck or not, and starts over. Behind the "Try again"
     * button, so a hang never needs an app restart.
     */
    fun reconnect() {
        automaticRetries = 0
        dropBinding()
        evaluate()
    }

    /** Unbinds and stops the helper; remove = true also ends a half-started service process. */
    private fun dropBinding() {
        watchdog?.cancel()
        if (Shizuku.pingBinder()) runCatching { Shizuku.unbindUserService(args, connection, true) }
        service = null
        bindRequested = false
    }

    fun isShizukuInstalled(): Boolean =
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(SHIZUKU_PACKAGE, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
            }
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }

    suspend fun listProcesses(): Reply<ProcessSnapshot> = call(ProcessSnapshot.serializer()) { it.listProcesses() }

    suspend fun listWatchers(userId: Int = currentUserId()): Reply<WatchersSnapshot> =
        call(WatchersSnapshot.serializer()) { it.listWatchers(userId) }

    suspend fun appDetail(
        packageName: String,
        userId: Int,
    ): Reply<AppDetail> = call(AppDetail.serializer()) { it.appDetail(packageName, userId) }

    suspend fun wakeups(windowMs: Long): Reply<WakeupReport> = call(WakeupReport.serializer()) { it.wakeups(windowMs) }

    suspend fun planAction(action: Action): Reply<ActionPlan> =
        call(ActionPlan.serializer()) { it.planAction(PayloadCodec.encode(Action.serializer(), action)) }

    suspend fun planDisableAccessibility(
        packageName: String,
        userId: Int,
        component: String,
    ): Reply<ActionPlan> = call(ActionPlan.serializer()) { it.planDisableAccessibility(packageName, userId, component) }

    suspend fun executePlan(plan: ActionPlan): Reply<ActionOutcome> =
        call(ActionOutcome.serializer()) { it.executePlan(PayloadCodec.encode(ActionPlan.serializer(), plan)) }

    /** PSS in kB; expensive, so only on explicit request. */
    suspend fun pss(pid: Int): Reply<Long> = call(Long.serializer()) { it.pss(pid) }

    private fun currentUserId(): Int = android.os.Process.myUid() / Uids.PER_USER_RANGE

    /**
     * Runs one typed operation on the IO dispatcher. Transport failures (service gone, protocol
     * mismatch) become [ServiceError]s as well, so callers handle a single result type.
     */
    suspend fun <T> call(
        serializer: KSerializer<T>,
        operation: (IPicketService) -> ByteArray,
    ): Reply<T> =
        withContext(ioDispatcher) {
            val current =
                service ?: return@withContext Reply.failed(
                    ServiceError(ServiceError.Kind.UNSUPPORTED, "Privileged service is not connected"),
                )
            val start = SystemClock.elapsedRealtimeNanos()
            try {
                val bytes = operation(current)
                val received = SystemClock.elapsedRealtimeNanos()
                val reply = PayloadCodec.decodeReply(serializer, bytes)
                val decoded = SystemClock.elapsedRealtimeNanos()
                mutableLastCall.value =
                    CallTiming(
                        roundTripMicros = (received - start) / 1_000,
                        decodeMicros = (decoded - received) / 1_000,
                        payloadBytes = bytes.size,
                        serviceWallMicros = reply.cost.wallMicros,
                        serviceCpuMicros = reply.cost.cpuMicros,
                        filesRead = reply.cost.filesRead,
                    )
                reply
            } catch (e: RemoteException) {
                Log.w(TAG, "Privileged call failed", e)
                service = null
                bindRequested = false
                scope.launch(mainDispatcher) { evaluate() }
                Reply.failed(ServiceError(ServiceError.Kind.INTERNAL, "Privileged service died: ${e.message}"))
            } catch (e: SecurityException) {
                Reply.failed(ServiceError(ServiceError.Kind.NOT_PERMITTED, e.message.orEmpty()))
            }
        }

    private fun bind() {
        val current = service
        if (current != null && current.asBinder().pingBinder()) return
        if (bindRequested) return
        bindRequested = true
        attempt++
        mutableState.value = PrivilegedState.Connecting
        try {
            Shizuku.bindUserService(args, connection)
        } catch (e: RuntimeException) {
            fail("Could not start the privileged service: ${e.message}")
            return
        }
        armWatchdog()
    }

    /**
     * Shizuku reports nothing when the helper process dies while starting or a bind request is
     * lost, so without a deadline the app would show "Connecting" until it is restarted. After
     * [CONNECT_TIMEOUT_MS] the binding is dropped and retried once, then reported as failed.
     */
    private fun armWatchdog() {
        watchdog?.cancel()
        watchdog =
            scope.launch(mainDispatcher) {
                delay(CONNECT_TIMEOUT_MS)
                if (mutableState.value !is PrivilegedState.Connecting) return@launch
                Log.w(TAG, "No connection after $CONNECT_TIMEOUT_MS ms (automatic retries: $automaticRetries)")
                dropBinding()
                if (automaticRetries < MAX_AUTOMATIC_RETRIES) {
                    automaticRetries++
                    evaluate()
                } else {
                    fail("The privileged helper did not start within ${CONNECT_TIMEOUT_MS / 1_000} seconds.")
                }
            }
    }

    private fun fail(message: String) {
        watchdog?.cancel()
        bindRequested = false
        service = null
        mutableState.value = PrivilegedState.Failed(message)
    }

    /** Runs on the IO dispatcher: binder calls may block. */
    private fun handshake(candidate: IPicketService): Result<ServiceIdentity> =
        try {
            val protocol = candidate.protocolVersion()
            if (protocol != PayloadCodec.PROTOCOL_VERSION) {
                Result.failure(
                    IllegalStateException(
                        "Privileged service speaks protocol $protocol, app expects ${PayloadCodec.PROTOCOL_VERSION}",
                    ),
                )
            } else {
                Result.success(
                    ServiceIdentity(
                        uid = candidate.uid(),
                        seLinuxContext = candidate.seLinuxContext(),
                        serverVersion = Shizuku.getVersion(),
                        isSui = Sui.isSui(),
                    ),
                )
            }
        } catch (e: RemoteException) {
            Result.failure(IllegalStateException("Privileged service did not answer: ${e.message}", e))
        } catch (e: SecurityException) {
            Result.failure(IllegalStateException("Privileged service refused the app: ${e.message}", e))
        } catch (e: RuntimeException) {
            Result.failure(IllegalStateException("Handshake failed: ${e.message}", e))
        }

    /** Main thread: commits the handshake unless the attempt was abandoned meanwhile. */
    private fun finishConnect(
        token: Int,
        candidate: IPicketService,
        result: Result<ServiceIdentity>,
    ) {
        if (token != attempt || !bindRequested) return
        result
            .onSuccess { identity ->
                watchdog?.cancel()
                automaticRetries = 0
                service = candidate
                mutableState.value = PrivilegedState.Connected(identity)
            }.onFailure { fail(it.message.orEmpty()) }
    }

    companion object {
        const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        const val PROCESS_SUFFIX = "privileged"
        private const val PERMISSION_REQUEST_CODE = 0x5049
        private const val TAG = "PrivilegedClient"

        /** A cold start of the helper takes about one second; this leaves room for slow phones. */
        const val CONNECT_TIMEOUT_MS = 10_000L
        private const val MAX_AUTOMATIC_RETRIES = 1
    }
}

/** Cost of one privileged call as seen from both ends. */
data class CallTiming(
    val roundTripMicros: Long,
    val decodeMicros: Long,
    val payloadBytes: Int,
    val serviceWallMicros: Long,
    val serviceCpuMicros: Long,
    val filesRead: Int,
)
