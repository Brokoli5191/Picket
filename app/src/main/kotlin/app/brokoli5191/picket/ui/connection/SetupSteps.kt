package app.brokoli5191.picket.ui.connection

import app.brokoli5191.picket.privileged.client.PrivilegedState

/** The three things a user does once before Picket works, in this order. */
enum class SetupStep { INSTALL, START, ALLOW }

enum class StepStatus { DONE, CURRENT, PENDING }

/**
 * Where each setup step stands in [state]. While Picket is still checking, nothing is known and
 * every step is pending; once the helper is connecting, connected or has failed, all three are done.
 * A Shizuku that is too old counts as not installed: updating it is the next thing to do, and an
 * update stops its server anyway.
 */
fun stepStatuses(state: PrivilegedState): Map<SetupStep, StepStatus> {
    val current =
        when (state) {
            PrivilegedState.Checking -> return SetupStep.entries.associateWith { StepStatus.PENDING }
            PrivilegedState.NotInstalled, is PrivilegedState.Unsupported -> SetupStep.INSTALL
            PrivilegedState.NotRunning -> SetupStep.START
            PrivilegedState.PermissionRequired, PrivilegedState.PermissionDenied -> SetupStep.ALLOW
            PrivilegedState.Connecting, is PrivilegedState.Connected, is PrivilegedState.Failed -> null
        }
    return SetupStep.entries.associateWith { step ->
        when {
            current == null || step < current -> StepStatus.DONE
            step == current -> StepStatus.CURRENT
            else -> StepStatus.PENDING
        }
    }
}
