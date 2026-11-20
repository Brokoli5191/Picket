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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
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
                if (binder == null || !binder.pingBinder()) {
                    mutableState.value = PrivilegedState.Failed("The privileged service returned a dead binder")
                    return
                }
                val candidate = IPicketService.Stub.asInterface(binder)
                scope.launch(ioDispatcher) { handshake(candidate) }
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
        if (Shizuku.pingBinder()) runCatching { Shizuku.unbindUserService(args, connection, true) }
        service = null
        bindRequested = false
        evaluate()
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
        mutableState.value = PrivilegedState.Connecting
        try {
            Shizuku.bindUserService(args, connection)
        } catch (e: RuntimeException) {
            bindRequested = false
            mutableState.value = PrivilegedState.Failed("Could not start the privileged service: ${e.message}")
        }
    }

    private fun handshake(candidate: IPicketService) {
        try {
            val protocol = candidate.protocolVersion()
            if (protocol != PayloadCodec.PROTOCOL_VERSION) {
                mutableState.value =
                    PrivilegedState.Failed(
                        "Privileged service speaks protocol $protocol, app expects ${PayloadCodec.PROTOCOL_VERSION}",
                    )
                return
            }
            val identity =
                ServiceIdentity(
                    uid = candidate.uid(),
                    seLinuxContext = candidate.seLinuxContext(),
                    serverVersion = Shizuku.getVersion(),
                    isSui = Sui.isSui(),
                )
            service = candidate
            mutableState.value = PrivilegedState.Connected(identity)
        } catch (e: RemoteException) {
            mutableState.value = PrivilegedState.Failed("Privileged service did not answer: ${e.message}")
        } catch (e: SecurityException) {
            mutableState.value = PrivilegedState.Failed("Privileged service refused the app: ${e.message}")
        }
    }

    companion object {
        const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        const val PROCESS_SUFFIX = "privileged"
        private const val PERMISSION_REQUEST_CODE = 0x5049
        private const val TAG = "PrivilegedClient"
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
