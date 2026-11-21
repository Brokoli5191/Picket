package app.brokoli5191.picket.privileged.service

import android.os.Process
import app.brokoli5191.picket.privileged.IPicketService
import kotlin.system.exitProcess

/**
 * The privileged half of Picket. Shizuku starts it in a separate process that runs as the shell
 * user (or root with Sui) and hands its binder to the app.
 */
class PicketUserService : IPicketService.Stub() {
    override fun destroy() {
        exitProcess(0)
    }

    override fun protocolVersion(): Int = PROTOCOL_VERSION

    override fun uid(): Int = Process.myUid()

    companion object {
        const val PROTOCOL_VERSION = 1
    }
}
