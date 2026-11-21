package app.brokoli5191.picket.ui.connection

import app.brokoli5191.picket.privileged.client.PrivilegedState
import app.brokoli5191.picket.privileged.client.ServiceIdentity
import app.brokoli5191.picket.ui.connection.StepStatus.CURRENT
import app.brokoli5191.picket.ui.connection.StepStatus.DONE
import app.brokoli5191.picket.ui.connection.StepStatus.PENDING
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SetupStepsTest {
    private fun statuses(state: PrivilegedState) = SetupStep.entries.map { stepStatuses(state).getValue(it) }

    @Test
    fun nothingIsKnownWhileChecking() {
        assertThat(statuses(PrivilegedState.Checking)).containsExactly(PENDING, PENDING, PENDING).inOrder()
    }

    @Test
    fun aMissingOrOutdatedShizukuIsTheFirstStep() {
        assertThat(statuses(PrivilegedState.NotInstalled)).containsExactly(CURRENT, PENDING, PENDING).inOrder()
        assertThat(statuses(PrivilegedState.Unsupported(10))).containsExactly(CURRENT, PENDING, PENDING).inOrder()
    }

    @Test
    fun aStoppedShizukuIsTheSecondStep() {
        assertThat(statuses(PrivilegedState.NotRunning)).containsExactly(DONE, CURRENT, PENDING).inOrder()
    }

    @Test
    fun permissionIsTheThirdStepWhetherAskedOrDenied() {
        assertThat(statuses(PrivilegedState.PermissionRequired)).containsExactly(DONE, DONE, CURRENT).inOrder()
        assertThat(statuses(PrivilegedState.PermissionDenied)).containsExactly(DONE, DONE, CURRENT).inOrder()
    }

    @Test
    fun everyStepIsDoneOnceTheHelperIsStarting() {
        val connected =
            PrivilegedState.Connected(
                ServiceIdentity(uid = 2000, seLinuxContext = "u:r:shell:s0", serverVersion = 13, isSui = false),
            )
        for (state in listOf(PrivilegedState.Connecting, connected, PrivilegedState.Failed("boom"))) {
            assertThat(statuses(state)).containsExactly(DONE, DONE, DONE).inOrder()
        }
    }
}
