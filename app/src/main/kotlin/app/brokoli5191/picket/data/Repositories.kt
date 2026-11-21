package app.brokoli5191.picket.data

import app.brokoli5191.picket.core.ipc.Reply
import app.brokoli5191.picket.core.model.AppDetail
import app.brokoli5191.picket.core.model.WakeupReport
import app.brokoli5191.picket.core.model.WatchersSnapshot

/** Who holds privileged hooks. */
fun interface WatchersRepository {
    suspend fun load(): Reply<WatchersSnapshot>
}

/** Who woke the device within a window. */
fun interface WakeupsRepository {
    suspend fun load(windowMs: Long): Reply<WakeupReport>
}

/** Everything about one app that needs shell privileges to see. */
fun interface AppDetailRepository {
    suspend fun load(
        packageName: String,
        userId: Int,
    ): Reply<AppDetail>
}
