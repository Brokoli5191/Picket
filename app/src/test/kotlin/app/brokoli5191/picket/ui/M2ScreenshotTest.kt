package app.brokoli5191.picket.ui

import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import app.brokoli5191.picket.core.process.GroupKey
import app.brokoli5191.picket.core.process.GroupLabel
import app.brokoli5191.picket.core.process.GroupRow
import app.brokoli5191.picket.core.process.Importance
import app.brokoli5191.picket.core.process.ProcessKey
import app.brokoli5191.picket.core.process.ProcessRow
import app.brokoli5191.picket.privileged.client.PrivilegedState
import app.brokoli5191.picket.ui.connection.ConnectionScreen
import app.brokoli5191.picket.ui.processes.ProcessesScreen
import app.brokoli5191.picket.ui.processes.ProcessesUiState
import app.brokoli5191.picket.ui.theme.PicketTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class M2ScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun connectionNotRunning() {
        compose.setContent {
            PicketTheme(darkTheme = false, dynamicColor = false) {
                Surface { ConnectionScreen(PrivilegedState.NotRunning, {}, {}, {}) }
            }
        }
        compose.onRoot().captureRoboImage("connection-not-running-light.png")
    }

    @Test
    fun processesDark() {
        compose.setContent {
            PicketTheme(darkTheme = true, dynamicColor = false) {
                Surface { ProcessesScreen(sampleState(), {}, {}, {}, {}, {}) }
            }
        }
        compose.onRoot().captureRoboImage("processes-basic-dark.png")
    }

    private fun sampleState(): ProcessesUiState {
        fun process(
            pid: Int,
            name: String,
            cpu: Double,
            rssMb: Long,
        ) = ProcessRow(
            pid,
            ProcessKey(pid, pid * 10L),
            name,
            "u0_a123",
            10_123,
            'S',
            12,
            rssMb * 1024 * 1024,
            cpu,
            3_600_000,
            0,
            Importance.FOREGROUND,
        )
        return ProcessesUiState(
            loading = false,
            totalCpuPercent = 23.4,
            processCount = 412,
            groups =
                listOf(
                    GroupRow(
                        GroupKey.App(10_123),
                        GroupLabel("Chat", "com.example.chat", isSystem = false),
                        listOf(
                            process(4242, "com.example.chat", 12.5, 210),
                            process(4243, "com.example.chat:push", 0.4, 38),
                        ),
                    ),
                    GroupRow(
                        GroupKey.App(10_150),
                        GroupLabel("Maps", "com.example.maps", isSystem = false),
                        listOf(process(5150, "com.example.maps", 3.2, 312)),
                    ),
                ),
        )
    }
}
